package document.generation

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Graphe koog de generation AsciiDoc assistee par IA (EPIC DOC-2).
 *
 * Architecture koog+langchain4j (EPIC L — koog orchestre, langchain4j execute) :
 * ```
 * buildPrompt -> callLlm -> validateAsciiDoc -> finish (ou error)
 * ```
 *
 * - **buildPrompt** : assemble le prompt final (system + user) — koog pur.
 * - **callLlm** : invoque le LLM via [DocumentLlmProvider] — langchain4j.
 * - **validateAsciiDoc** : verifie que la sortie est un AsciiDoc valide — koog pur.
 * - **finish** / **error** : nœuds terminaux.
 *
 * Loi de l'Economie d'Encre : la tache Gradle [document.GenerateDocumentTask]
 * verifie l'existence d'un artefact valide avant d'invoquer ce graphe.
 *
 * Source pattern : `codebase.koog.VibecodingGraph` (Queens) + KOOG_AGENTIC_PATTERNS.adoc.
 */
class DocumentGenerationGraph(
    private val llmProvider: DocumentLlmProvider,
) {

    private val log = LoggerFactory.getLogger(DocumentGenerationGraph::class.java)

    val graph: AIAgentGraphStrategy<DocumentGenerationState, DocumentGenerationState> =
        strategy<DocumentGenerationState, DocumentGenerationState>(
            name = "document-generation",
            toolSelectionStrategy = ToolSelectionStrategy.NONE,
        ) {
            val buildPrompt by node<DocumentGenerationState, DocumentGenerationState> { state ->
                buildPromptNode(state)
            }

            val callLlm by node<DocumentGenerationState, DocumentGenerationState> { state ->
                callLlmNode(state)
            }

            val validateAsciiDoc by node<DocumentGenerationState, DocumentGenerationState> { state ->
                validateAsciiDocNode(state)
            }

            val finish by node<DocumentGenerationState, DocumentGenerationState> { state ->
                state
            }

            val error by node<DocumentGenerationState, DocumentGenerationState> { state ->
                state
            }

            edge(nodeStart forwardTo buildPrompt onCondition { _ -> true } transformed { it })
            edge(buildPrompt forwardTo callLlm onCondition { _ -> true } transformed { it })
            edge(callLlm forwardTo validateAsciiDoc onCondition { _ -> true } transformed { it })
            edge(validateAsciiDoc forwardTo finish onCondition { it.document.isNotEmpty() } transformed { it })
            edge(validateAsciiDoc forwardTo error onCondition { it.error != null } transformed { it })
            edge(finish forwardTo nodeFinish onCondition { _ -> true } transformed { it })
            edge(error forwardTo nodeFinish onCondition { _ -> true } transformed { it })
        }

    /**
     * Point d'entree principal — execute le graphe et retourne l'etat final.
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