package document

import org.asciidoctor.SafeMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Publishes the multi-language book corpus into every requested format
 * (EPIC DOC-BOOK-PUBLISH, US-2).
 *
 * The task consumes the `book-<lang>.adoc` files produced by
 * [TranslateBookAllLanguagesTask] and converts each into
 * `book-<lang>.<ext>` for every format of `publishFormats`. The plan is derived
 * purely: [BookLanguagePlanner] resolves the languages from the N0
 * `LanguageCatalog` (decision D2), then [BookPublicationPlanner] crosses them
 * with the requested [DocumentFormat]s (decision D3). The conversion reuses
 * [DocumentConverter] — the same engine as the mono-source `convertDocumentTo*`
 * tasks (decision D6) — including the Ink Economy Law skips by source hash.
 *
 * Ink Economy Law: opt-in (no format → no work) and `skipExisting` skips a
 * target whose output already exists. The task is non-incrementable
 * ([DisableCachingByDefault]).
 *
 * The produced files are declared through an [OutputFiles] collection (piège
 * #40 — the `docs/document/` directory is shared, an `@OutputDirectory` would
 * conflict), populated by a provider that mirrors the resolved plan exactly.
 */
@DisableCachingByDefault(because = "Conversion idempotence is applicative — source hash is stored in generated file metadata")
abstract class PublishBookAllLanguagesTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val sourceLanguage: Property<String>

    /** DOC-BOOK-MULTILANG — expand the plan to the whole catalog minus the source. */
    @get:Input
    abstract val translateToAll: Property<Boolean>

    /** DOC-BOOK-MULTILANG — explicit target-language subset (takes precedence). */
    @get:Input
    abstract val targetLanguages: ListProperty<String>

    /** DOC-BOOK-PUBLISH — the requested output formats (lowercase names). */
    @get:Input
    abstract val publishFormats: ListProperty<String>

    /** DOC-BOOK-PUBLISH — base name shared by the source and output books (default `book`). */
    @get:Input
    abstract val outputFileName: Property<String>

    /**
     * DOC-BOOK-PUBLISH (US-5) — also publish the assembled *source* book
     * (`book.adoc`), not only the translated books. Off by default (decision D8).
     */
    @get:Input
    abstract val includeSource: Property<Boolean>

    /** DOC-BOOK-PUBLISH — do not overwrite an already published book. */
    @get:Input
    abstract val skipExisting: Property<Boolean>

    /** AsciidoctorJ safe-mode guard applied to every conversion (DOC-CR3-2). */
    @get:Input
    abstract val safeMode: Property<SafeMode>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val pdfThemeFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val htmlStylesheetFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val epubStylesheetFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val logoFile: RegularFileProperty

    /** The directory the translated books live in and the outputs are written to. */
    @get:Internal
    abstract val outputDir: DirectoryProperty

    /** DOC-BOOK-PUBLISH — the resolved published books (one per language × format). */
    @get:OutputFiles
    abstract val outputFiles: ConfigurableFileCollection

    init {
        group = "document"
        description = "Publishes every translated book (book-<lang>.adoc) into each requested format. — DOC-BOOK-PUBLISH"
        sourceLanguage.convention("fr")
        translateToAll.convention(false)
        targetLanguages.convention(emptyList())
        publishFormats.convention(emptyList())
        outputFileName.convention("book")
        includeSource.convention(false)
        skipExisting.convention(false)
        safeMode.convention(SafeMode.UNSAFE)
    }

    @get:Internal
    internal val theme: DocumentTheme
        get() = DocumentTheme(
            pdfTheme = pdfThemeFile.orNull?.asFile,
            htmlStylesheet = htmlStylesheetFile.orNull?.asFile,
            epubStylesheet = epubStylesheetFile.orNull?.asFile,
            logo = logoFile.orNull?.asFile,
        )

    @TaskAction
    fun publishBookAllLanguages() {
        val logger = LoggerFactory.getLogger(PublishBookAllLanguagesTask::class.java)
        val source = sourceLanguage.getOrElse("fr")
        val baseName = outputFileName.getOrElse("book")
        val destination = outputDir.orNull?.asFile ?: throw GradleException("$name — outputDir is not set")

        val languages = BookLanguagePlanner.plan(
            sourceLanguage = source,
            requested = targetLanguages.getOrElse(emptyList()),
            translateToAll = translateToAll.getOrElse(false),
        )
        val plan = BookPublicationPlanner.plan(
            languages,
            baseName,
            publishFormats.getOrElse(emptyList()),
            sourceLanguage = source,
            includeSource = includeSource.getOrElse(false),
        )

        // Ink Economy Law — no format (or no language) selected is a strict
        // no-op: the pipeline is left untouched (backward compatible).
        if (plan.isEmpty()) {
            logger.info("{} — no publish format configured, skipping publication", name)
            return
        }

        val docTheme = theme
        var published = 0
        plan.forEach { target ->
            val sourceFile = File(destination, target.sourceFileName)
            if (!sourceFile.isFile) {
                logger.warn("{} — source '{}' missing, skipping (run translateBookAllLanguages first)", name, sourceFile.name)
                return@forEach
            }

            val outputFile = File(destination, target.outputFileName)
            if (skipExisting.getOrElse(false) && outputFile.isFile) {
                logger.info("{} — '{}' already exists, skipping (Ink Economy Law)", name, outputFile.name)
                return@forEach
            }

            publish(DocumentSource(sourceFile), outputFile, target.format, docTheme, logger)
            published++
        }

        logger.info("{} — published {}/{} books (source language: {})", name, published, plan.size, source)
    }

    /**
     * Converts one source book into one output format, mirroring
     * [ConvertDocumentTask]: text formats carry an in-band metadata header
     * (source hash), binary formats (PDF/EPUB) carry a `<output>.sourcehash`
     * sidecar. The Ink Economy Law checks are delegated to [DocumentConverter].
     */
    private fun publish(
        source: DocumentSource,
        output: File,
        format: DocumentFormat,
        docTheme: DocumentTheme,
        logger: org.slf4j.Logger,
    ) {
        when (format) {
            DocumentFormat.PDF, DocumentFormat.EPUB -> {
                if (DocumentConverter.shouldSkipBinaryConversion(source, output)) {
                    logger.info("{} skip — '{}' exists for unchanged source", name, output.name)
                    return
                }
                output.parentFile.mkdirs()
                DocumentConverter.convertToFile(source, format.backend, output, docTheme, safeMode.get())
                DocumentConverter.writeBinaryMetadataHeader(source, output)
            }
            else -> {
                if (DocumentConverter.shouldSkipConversion(source, output)) {
                    logger.info("{} skip — '{}' exists for unchanged source", name, output.name)
                    return
                }
                val content = DocumentConverter.convert(source, format.backend, docTheme, safeMode.get())
                output.parentFile.mkdirs()
                output.writeText(DocumentConverter.buildMetadataHeader(source) + content)
            }
        }
        logger.info("{} — converted '{}' -> {} ({} bytes)", name, source.name, output.name, output.length())
    }
}
