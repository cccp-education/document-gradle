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

        val toc = tocFile.orNull?.asFile
        val tocPresent = toc != null && toc.exists()
        val sections = if (tocPresent) BookTocParser.parse(toc!!) else emptyList()

        val result = if (tocPresent && sections.isNotEmpty()) {
            // DOC-BOOK-DOMAIN-3 — structured, navigable assembly from the TOC tree
            val tree = BookTreeBuilder.fromSections(sections)
            BookAssembler.assemble(
                tree = tree,
                layout = BookLayout(),
                title = title.get(),
                author = author.get(),
                resolveContent = BookAssembler.fpaAwareResolver(pages),
            )
        } else {
            BookAssembler.assemble(pages, title.get(), author.get(), photos)
        }

        result.writeTo(output)
        logger.info(
            "{} — assembled book -> {} ({} bytes, structured={})",
            name,
            output.absolutePath,
            output.length(),
            tocPresent && sections.isNotEmpty(),
        )

        // FPA-BOOK-6 — locate OCR / LLM-vision failures for human iteration: the
        // report carries page number + owning TOC section ref + title.
        val issues = if (tocPresent) BookOcrFailureDetector.detect(pages, sections) else emptyList()
        if (issues.isNotEmpty()) {
            val reportFile = output.parentFile.resolve("book-ocr-issues.json")
            BookOcrIssueReport.write(issues, reportFile)
            logger.warn(
                "{} — {} OCR failure(s) located for human iteration, see {}",
                name,
                issues.size,
                reportFile.absolutePath,
            )
        }

        validateIfConfigured(logger, pages, toc, sections)
    }

    private fun validateIfConfigured(
        logger: org.slf4j.Logger,
        pages: File,
        toc: File?,
        sections: List<BookSection>,
    ) {
        val tocFile = toc ?: tocFile.orNull?.asFile ?: return
        if (!tocFile.exists()) {
            logger.warn("{} — tocFile '{}' not found, skipping validation", name, tocFile.absolutePath)
            return
        }

        val pdfs = pdfsDir.orNull?.asFile

        // DOC-BOOK-DOMAIN-6 — tree-level structural validation (ref continuity,
        // uniqueness, matter completeness, page order monotonicity), merged with
        // the file-level validation (DOC-BOOK-VALIDATE) into a single report.
        val structural = BookValidator.validateStructure(sections)
        val fileBased = BookValidator.validate(pagesDir = pages, toc = sections, pdfsDir = pdfs)
        val reasons = buildList {
            if (structural is BookValidationResult.Invalid) {
                addAll(structural.reasons.map { "structure — $it" })
            }
            if (fileBased is BookValidationResult.Invalid) addAll(fileBased.reasons)
        }

        if (reasons.isEmpty()) {
            logger.info(
                "{} — book validation OK ({} pages covered by TOC, structure coherent)",
                name,
                (fileBased as? BookValidationResult.Valid)?.pageCount ?: sections.size,
            )
        } else {
            when (validationMode.get()) {
                ValidationMode.LENIENT -> reasons.forEach {
                    logger.warn("{} — book validation (lenient): {}", name, it)
                }
                ValidationMode.STRICT -> BookValidator.enforce(ValidationMode.STRICT, reasons)
            }
        }
    }
}