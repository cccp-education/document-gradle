package document

import document.generation.DocumentGenerationGraph
import document.generation.DocumentGenerationState
import document.generation.DocumentLlmProviderFactory
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
import java.security.MessageDigest

/**
 * Tache `generateDocument` — generation AsciiDoc (DOC-2).
 *
 * Deux modes :
 * 1. **Mode IA** (si `prompt` est set) : invoque le graphe koog [DocumentGenerationGraph]
 *    qui orchestre buildPrompt -> callLlm -> validateAsciiDoc. Le LLM (Ollama local,
 *    langchain4j) produit un AsciiDoc structure depuis le prompt. Mode configurable
 *    via `llmMode` ("ollama" production | "fake" tests).
 * 2. **Mode copie** (sinon) : copie la source AsciiDoc vers la sortie (fallback DOC-1).
 *
 * Loi de l'Economie d'Encre : si la sortie existe et que le hash du prompt/source
 * correspond au hash stocke en metadata du fichier genere, on ne regenere pas.
 * L'IA est un service metered — ne pas re-invoquer pour un resultat identique.
 */
@DisableCachingByDefault(because = "Idempotence is applicative — source/prompt hash is stored in generated file metadata")
abstract class GenerateDocumentTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val outputFileName: Property<String>

    @get:Input
    @get:Optional
    abstract val prompt: Property<String>

    @get:Input
    abstract val llmMode: Property<String>

    @get:Input
    @get:Optional
    abstract val systemPrompt: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        description = "Genere un document AsciiDoc (IA via koog+langchain4j si prompt set, sinon copie source)."
    }

    @TaskAction
    fun generate() {
        val output = outputFile.get().asFile
        val logger = LoggerFactory.getLogger(GenerateDocumentTask::class.java)
        val mode = llmMode.getOrElse("ollama")
        val promptText = prompt.orNull

        if (promptText != null && promptText.isNotBlank()) {
            generateWithLlm(promptText, mode, output, logger)
        } else {
            copySource(output, logger)
        }
    }

    private fun generateWithLlm(
        promptText: String,
        mode: String,
        output: java.io.File,
        logger: org.slf4j.Logger,
    ) {
        val systemPromptText = systemPrompt.orNull ?: ""
        val inputHash = sha256(promptText + "|" + systemPromptText)

        if (output.exists() && outputHashMatches(output, inputHash)) {
            logger.info("generateDocument skip — sortie existante pour le meme prompt (hash {}) : {}", inputHash.take(8), output.absolutePath)
            return
        }

        val provider = DocumentLlmProviderFactory.create(mode)
        val graph = DocumentGenerationGraph(provider)
        val initial = DocumentGenerationState(
            prompt = promptText,
            systemPrompt = systemPromptText,
        )
        val result = graph.execute(initial)

        if (result.error != null) {
            logger.warn("generateDocument — erreur de generation IA : {}", result.error)
            throw IllegalStateException("Document generation failed: ${result.error}")
        }

        output.parentFile.mkdirs()
        output.writeText(buildMetadataHeader(inputHash) + result.document)
        logger.info("generateDocument — IA ({}) -> {} ({} chars)", mode, output.absolutePath, result.document.length)
    }

    private fun copySource(output: java.io.File, logger: org.slf4j.Logger) {
        val source = sourceFile.orNull?.asFile
        if (source == null || !source.exists()) {
            logger.warn("generateDocument — source absente et aucun prompt defini, rien a faire.")
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

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun buildMetadataHeader(hash: String): String =
        "// document-gradle generated — prompt hash: $hash\n"

    private fun outputHashMatches(output: java.io.File, expectedHash: String): Boolean {
        val firstLine = output.bufferedReader().use { it.readLine() } ?: return false
        return firstLine.contains("prompt hash: $expectedHash")
    }
}