package document

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import java.io.File
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

/**
 * Assembles OCR-ed AsciiDoc pages (produced by codex-gradle) into a single
 * book document.
 *
 * DOC-11 — Book pipeline Codex -> Document:
 * - Reads all `.adoc` pages from [pagesDir] (the N2 <-> N2 bridge)
 * - Orders them by numeric prefix (PageOrder)
 * - Merges their content under a single AsciiDoc header
 * - Embeds original photos from [photosDir] as `image::` directives
 * - Writes the assembled book to [outputFile]
 *
 * The source page files are never mutated (Rule 7).
 *
 * DOC-BOOK-VALIDATE-2 — optional post-assembly validation against the book
 * TOC ([tocFile]) and original PDFs ([pdfsDir]) via [BookValidator] and
 * [BookTocParser]. [validationMode] controls how findings are surfaced:
 * [ValidationMode.LENIENT] logs warnings and never fails the build (default),
 * [ValidationMode.STRICT] fails the build with a [GradleException].
 */
@DisableCachingByDefault(because = "Idempotence is applicative — deterministic assembly from ordered pages")
abstract class AssembleBookTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val pagesDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val photosDir: DirectoryProperty

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val author: Property<String>

    @get:Input
    @get:Optional
    abstract val outputFileName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val tocFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val pdfsDir: DirectoryProperty

    @get:Input
    abstract val validationMode: Property<ValidationMode>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        validationMode.convention(ValidationMode.LENIENT)
    }

    @TaskAction
    fun assemble() {
        val logger = LoggerFactory.getLogger(AssembleBookTask::class.java)
        val pages = pagesDir.orNull?.asFile
        val photos = photosDir.orNull?.asFile
        val output = outputFile.get().asFile

        if (pages == null || !pages.isDirectory) {
            logger.warn("{} — pages directory missing, skipping assembly", name)
            return
        }

        val result = BookAssembler.assemble(
            pagesDir = pages,
            title = title.get(),
            author = author.get(),
            photosDir = photos,
        )

        result.writeTo(output)
        logger.info(
            "{} — assembled {} pages ({} photos) -> {} ({} bytes)",
            name,
            result.pageCount,
            result.photoCount,
            output.absolutePath,
            output.length(),
        )

        validateIfConfigured(logger, pages)
    }

    private fun validateIfConfigured(
        logger: org.slf4j.Logger,
        pages: File,
    ) {
        val toc = tocFile.orNull?.asFile ?: return
        if (!toc.exists()) {
            logger.warn("{} — tocFile '{}' not found, skipping validation", name, toc.absolutePath)
            return
        }

        val sections = BookTocParser.parse(toc)
        val pdfs = pdfsDir.orNull?.asFile
        val validation = BookValidator.validate(pagesDir = pages, toc = sections, pdfsDir = pdfs)

        when (validation) {
            is BookValidationResult.Valid -> logger.info(
                "{} — book validation OK ({} pages covered by TOC)",
                name,
                validation.pageCount,
            )
            is BookValidationResult.Invalid -> {
                val reasons = validation.reasons
                when (validationMode.get()) {
                    ValidationMode.LENIENT -> reasons.forEach {
                        logger.warn("{} — book validation (lenient): {}", name, it)
                    }
                    ValidationMode.STRICT -> throw GradleException(
                        "$name — book validation failed (${reasons.size} finding(s)):\n" +
                            reasons.joinToString("\n") { "  - $it" },
                    )
                }
            }
        }
    }
}