package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory

/**
 * Tache de conversion AsciiDoc -> format (stub DOC-1).
 *
 * DOC-3/DOC-4/DOC-5 implementeront la conversion AsciidoctorJ reelle.
 * Pour DOC-1, la tache valide que la source existe et logge le format cible.
 */
abstract class ConvertDocumentTask : DefaultTask() {

    @get:InputFile
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

        logger.info("{} — stub : {} -> {} ({} backend)", name, source.name, output.name, fmt.backend)
    }
}