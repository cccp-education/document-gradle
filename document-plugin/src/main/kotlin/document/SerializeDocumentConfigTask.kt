package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task `serializeDocumentConfig` (DOC-12).
 *
 * Materialises the unified `document { }` DSL into `document-config.json`
 * via [DocumentConfigSerializer]. The JSON is the N3 contract consumed by
 * runner-gradle and downstream tooling — it mirrors the source, enrichment
 * toggles, output format toggles, theme and front-matter metadata of the
 * pipeline without invoking any LLM or conversion.
 *
 * Loi de l'Economie d'Encre : cette tache ne re-calcule pas de documents,
 * elle ne fait que serialiser une configuration deja resolue.
 */
@DisableCachingByDefault(because = "Idempotence is applicative — deterministic JSON serialisation of resolved config")
abstract class SerializeDocumentConfigTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    // Enrichment flags
    @get:Input
    abstract val enrichPlantUml: Property<Boolean>
    @get:Input
    abstract val enrichImages: Property<Boolean>
    @get:Input
    abstract val enrichPassthrough: Property<Boolean>

    // Outputs flags
    @get:Input
    abstract val outputHtml: Property<Boolean>
    @get:Input
    abstract val outputPdf: Property<Boolean>
    @get:Input
    abstract val outputEpub: Property<Boolean>
    @get:Input
    abstract val outputDocbook: Property<Boolean>
    @get:Input
    abstract val outputManpage: Property<Boolean>

    // Theme files
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

    // Metadata
    @get:Input
    abstract val metaTitle: Property<String>
    @get:Input
    @get:Optional
    abstract val metaAuthor: Property<String>
    @get:Input
    abstract val metaLanguage: Property<String>

    // Book (DOC-12 extension — book { } serialisation)
    @get:Input
    @get:Optional
    abstract val bookPagesDir: Property<String>
    @get:Input
    @get:Optional
    abstract val bookPhotosDir: Property<String>
    @get:Input
    @get:Optional
    abstract val bookTitle: Property<String>
    @get:Input
    @get:Optional
    abstract val bookAuthor: Property<String>

    init {
        group = "document"
    }

    @TaskAction
    fun execute() {
        val dir = outputDir.asFile.get()
        val sourceFileObj = sourceFile.orNull?.asFile

        val source: DocumentSource = if (sourceFileObj != null && sourceFileObj.exists()) {
            DocumentSource(sourceFileObj)
        } else {
            DocumentSource(File("source.adoc"))
        }

        val config = DocumentPipelineConfig(
            source = source,
            enrich = DocumentEnrichConfig(
                plantuml = enrichPlantUml.getOrElse(false),
                images = enrichImages.getOrElse(false),
                passthrough = enrichPassthrough.getOrElse(false),
            ),
            outputs = DocumentOutputs(
                html = outputHtml.getOrElse(true),
                pdf = outputPdf.getOrElse(false),
                epub = outputEpub.getOrElse(false),
                docbook = outputDocbook.getOrElse(false),
                manpage = outputManpage.getOrElse(false),
            ),
            theme = DocumentTheme(
                pdfTheme = pdfThemeFile.orNull?.asFile,
                htmlStylesheet = htmlStylesheetFile.orNull?.asFile,
                epubStylesheet = epubStylesheetFile.orNull?.asFile,
                logo = logoFile.orNull?.asFile,
            ),
            frontMatter = DocumentFrontMatter(
                title = metaTitle.getOrElse("Untitled Document"),
                author = metaAuthor.orNull,
                language = metaLanguage.getOrElse("fr"),
            ),
            book = BookConfig(
                pagesDir = bookPagesDir.orNull?.let { File(it) },
                photosDir = bookPhotosDir.orNull?.let { File(it) },
                title = bookTitle.orNull,
                author = bookAuthor.orNull,
            ),
        )

        val file = DocumentConfigSerializer().serialize(dir, config)
        logger.lifecycle(
            "[document] serializeDocumentConfig -> {} ({} bytes)",
            file.absolutePath,
            file.length(),
        )
    }
}