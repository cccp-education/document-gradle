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
 * Tache `generateDocument` — generation AsciiDoc (stub DOC-1).
 *
 * DOC-2 ajoutera la generation IA via langchain4j (gpt-oss:120b-cloud).
 * Pour DOC-1, la tache copie la source vers la sortie sans transformation.
 *
 * Regle de l'economie d'encre : si la sortie existe et couvre l'entree
 * (meme contenu), ne pas regenerer.
 */
abstract class GenerateDocumentTask : DefaultTask() {

    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        description = "Genere un document AsciiDoc (stub — DOC-2 ajoutera la generation IA via langchain4j)."
    }

    @TaskAction
    fun generate() {
        val source = sourceFile.get().asFile
        val output = outputFile.get().asFile
        val logger = LoggerFactory.getLogger(GenerateDocumentTask::class.java)

        if (!source.exists()) {
            logger.warn("generateDocument — source absente : {}", source.absolutePath)
            return
        }

        if (output.exists() && output.readText() == source.readText()) {
            logger.info("generateDocument skip — sortie existante identique : {}", output.absolutePath)
            return
        }

        output.parentFile.mkdirs()
        output.writeText(source.readText())
        logger.info("generateDocument — {} -> {}", source.absolutePath, output.absolutePath)
    }
}