package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

/**
 * Tache de conversion AsciiDoc -> format (AsciidoctorJ).
 *
 * DOC-3 : conversion HTML5 (backend html5) reelle.
 * DOC-4 : conversion PDF (backend pdf) — binaire, sidecar .sourcehash.
 * DOC-5 : conversions EPUB3 (binaire, sidecar), DocBook 5 / ManPage (texte, header metadata).
 *
 * Loi de l'Economie d'Encre : si la sortie existe et que le hash de la source
 * correspond au hash stocke en metadata du fichier genere, on ne re-convertit pas.
 */
@DisableCachingByDefault(because = "Idempotence is applicative — source hash is stored in generated file metadata")
abstract class ConvertDocumentTask() : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val format: Property<DocumentFormat>

    @get:Input
    @get:Optional
    abstract val outputFileName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
    }

    @TaskAction
    fun convert() {
        val source = sourceFile.get().asFile
        val fmt = format.get()
        val output = outputFile.get().asFile
        val logger = LoggerFactory.getLogger(ConvertDocumentTask::class.java)

        if (!source.exists()) {
            logger.warn("{} — source absente : {}", name, source.absolutePath)
            return
        }

        val docSource = DocumentSource(source)

        val content = when (fmt) {
            DocumentFormat.HTML -> DocumentConverter.convertToHtml(docSource)
            DocumentFormat.PDF -> {
                convertBinary(docSource, output, logger, "pdf")
                return
            }
            DocumentFormat.EPUB -> {
                convertBinary(docSource, output, logger, "epub3")
                return
            }
            DocumentFormat.DOCBOOK -> DocumentConverter.convertToDocBook(docSource)
            DocumentFormat.MANPAGE -> DocumentConverter.convertToManPage(docSource)
        }

        if (DocumentConverter.shouldSkipConversion(docSource, output)) {
            logger.info("{} skip — sortie existante pour source inchangee : {}", name, output.absolutePath)
            return
        }

        output.parentFile.mkdirs()
        output.writeText(DocumentConverter.buildMetadataHeader(docSource) + content)
        logger.info("{} — converti -> {} ({} octets)", name, output.absolutePath, output.length())
    }

    private fun convertBinary(
        docSource: DocumentSource,
        output: java.io.File,
        logger: org.slf4j.Logger,
        backend: String,
    ) {
        if (DocumentConverter.shouldSkipBinaryConversion(docSource, output)) {
            logger.info("{} skip — sortie {} existante pour source inchangee : {}", name, backend, output.absolutePath)
            return
        }
        logger.info("{} — {} -> {} ({} backend)", name, docSource.file.name, output.name, backend)
        DocumentConverter.convertToFile(docSource, backend, output)
        DocumentConverter.writeBinaryMetadataHeader(docSource, output)
        logger.info("{} — converti -> {} ({} octets)", name, output.absolutePath, output.length())
    }
}