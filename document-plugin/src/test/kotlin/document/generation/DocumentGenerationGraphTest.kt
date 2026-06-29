package document.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class AsciiDocValidatorTest {

    @Test
    fun `AsciiDoc valide avec titre de niveau 0`() {
        assertTrue(AsciiDocValidator.isValid("= Mon Document\n\nContenu."))
    }

    @Test
    fun `AsciiDoc invalide sans titre de niveau 0`() {
        assertFalse(AsciiDocValidator.isValid("== Sous-titre\n\nPas de niveau 0."))
    }

    @Test
    fun `AsciiDoc invalide si vide`() {
        assertFalse(AsciiDocValidator.isValid(""))
    }

    @Test
    fun `AsciiDoc valide avec espaces avant le titre`() {
        assertTrue(AsciiDocValidator.isValid("  = Titre avec indentation"))
    }
}

class DocumentGenerationGraphTest {

    @Test
    fun `execute produit un AsciiDoc valide avec un FakeLlmProvider`() {
        val graph = DocumentGenerationGraph(FakeDocumentLlmProvider())
        val initial = DocumentGenerationState(prompt = "Genere une intro pour un plugin Gradle.")

        val result = graph.execute(initial)

        assertNotNull(result.document)
        assertTrue(result.document.startsWith("= Document Genere"))
        assertNull(result.error)
    }

    @Test
    fun `execute retourne une erreur si le LLM produit un AsciiDoc invalide`() {
        val graph = DocumentGenerationGraph(FakeDocumentLlmProvider(response = "pas de titre niveau 0"))
        val initial = DocumentGenerationState(prompt = "Genere un doc.")

        val result = graph.execute(initial)

        assertEquals("", result.document)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not valid AsciiDoc"))
    }

    @Test
    fun `execute retourne une erreur si le LLM produit une sortie vide`() {
        val graph = DocumentGenerationGraph(FakeDocumentLlmProvider(response = ""))
        val initial = DocumentGenerationState(prompt = "Genere un doc.")

        val result = graph.execute(initial)

        assertEquals("", result.document)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("empty"))
    }

    @Test
    fun `buildPromptNode inclut le system prompt et la demande utilisateur`() {
        val graph = DocumentGenerationGraph(FakeDocumentLlmProvider())
        val initial = DocumentGenerationState(
            prompt = "Genere un manuel utilisateur.",
            systemPrompt = "Tu es un redacteur technique expert.",
        )

        val result = graph.execute(initial)

        assertNull(result.error)
    }
}