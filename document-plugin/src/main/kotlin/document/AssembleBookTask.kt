package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
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
    }
}