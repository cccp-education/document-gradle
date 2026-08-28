package document.generation

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Orchestrateur de generation AsciiDoc assistee par IA (EPIC DOC-2).
 *
 * Pipeline resilient (architecture koog+langchain4j, EPIC L — koog orchestre,
 * langchain4j execute) :
 * ```
 * buildPrompt -> callLlm -> validateAsciiDoc -> finish (ou error)
 * ```
 *
 * - **buildPrompt** : assemble le prompt final (system + user).
 * - **callLlm** : invoque le LLM via [DocumentLlmProvider] — langchain4j.
 * - **validateAsciiDoc** : verifie que la sortie est un AsciiDoc valide.
 * - **finish** / **error** : etats terminaux.
 *
 * Loi de l'Economie d'Encre : la tache Gradle [document.GenerateDocumentTask]
 * verifie l'existence d'un artefact valide avant d'invoquer cet orchestrateur.
 *
 * Source pattern : `codebase.koog.VibecodingGraph` (Queens) + KOOG_AGENTIC_PATTERNS.adoc.
 */
class DocumentGenerationGraph(
    private val llmProvider: DocumentLlmProvider,
) {

    private val log = LoggerFactory.getLogger(DocumentGenerationGraph::class.java)

    /**
     * Point d'entree principal — execute le pipeline et retourne l'etat final.
     * Resilient : toute exception devient une erreur dans le state (pas de crash).
     */
    fun execute(initialState: DocumentGenerationState): DocumentGenerationState {
        var state = initialState
        state = try {
            buildPromptNode(state)
        } catch (e: Exception) {
            log.warn("[DocumentGenerationGraph] buildPrompt failed: {}", e.message)
            return state.copy(error = "BuildPromptFailed: ${e.message}")
        }
        state = try {
            callLlmNode(state)
        } catch (e: Exception) {
            log.warn("[DocumentGenerationGraph] callLlm failed: {}", e.message)
            return state.copy(error = "LlmCallFailed: ${e.message}")
        }
        state = try {
            validateAsciiDocNode(state)
        } catch (e: Exception) {
            log.warn("[DocumentGenerationGraph] validateAsciiDoc failed: {}", e.message)
            return state.copy(error = "ValidationFailed: ${e.message}")
        }
        return state
    }

    private fun buildPromptNode(state: DocumentGenerationState): DocumentGenerationState {
        val full = buildString {
            if (state.systemPrompt.isNotBlank()) {
                appendLine(state.systemPrompt)
                appendLine()
            }
            appendLine("Genere un document AsciiDoc structure repondant a la demande suivante.")
            appendLine("Le document doit commencer par un titre de niveau 0 (= Titre).")
            appendLine("Utilise le sectionnement AsciiDoc standard (==, ===).")
            appendLine()
            appendLine("DEMANDE:")
            appendLine(state.prompt)
        }
        log.debug("[DocumentGenerationGraph] built prompt: {} chars", full.length)
        return state.copy(rawOutput = full)
    }

    private fun callLlmNode(state: DocumentGenerationState): DocumentGenerationState {
        val response = runBlocking { llmProvider.call(state.rawOutput) }
        log.info("[DocumentGenerationGraph] LLM response: {} chars", response.length)
        return state.copy(rawOutput = response)
    }

    private fun validateAsciiDocNode(state: DocumentGenerationState): DocumentGenerationState {
        val text = state.rawOutput.trim()
        if (text.isEmpty()) {
            return state.copy(error = "LLM output is empty")
        }
        if (!AsciiDocValidator.isValid(text)) {
            return state.copy(error = "LLM output is not valid AsciiDoc (missing level-0 title '= ...')")
        }
        return state.copy(document = text)
    }
}