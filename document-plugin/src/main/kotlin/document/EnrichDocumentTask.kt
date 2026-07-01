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
 * AsciiDoc enrichment task executed before multi-format conversion.
 *
 * DOC-9:
 * - preserves inline `[plantuml]` blocks (rendered at conversion time by Asciidoctor Diagram)
 * - preserves passthrough `++++` blocks (raw HTML) without escaping them
 * - recursively resolves `include::` directives (materialization for audit)
 *
 * Ink Economy Law: if the output already exists and the source hash matches
 * the hash stored in the enriched file metadata, enrichment is skipped.
 */
@DisableCachingByDefault(because = "Idempotence is applicative — source hash is stored in enriched file metadata")
abstract class EnrichDocumentTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val enrichPlantUml: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val enrichPassthrough: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val outputFileName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
    }

    @TaskAction
    fun enrich() {
        val source = sourceFile.get().asFile
        val output = outputFile.get().asFile
        val logger = LoggerFactory.getLogger(EnrichDocumentTask::class.java)

        if (!source.exists()) {
            logger.warn("{} — source missing: {}", name, source.absolutePath)
            return
        }

        val docSource = DocumentSource(source)

        if (DocumentConverter.shouldSkipConversion(docSource, output)) {
            logger.info("{} skip — enriched output exists for unchanged source: {}", name, output.absolutePath)
            return
        }

        val enriched = DocumentEnricher.enrich(docSource)

        output.parentFile.mkdirs()
        output.writeText(DocumentConverter.buildMetadataHeader(docSource) + enriched)
        logger.info("{} — enriched -> {} ({} bytes)", name, output.absolutePath, output.length())
    }
}