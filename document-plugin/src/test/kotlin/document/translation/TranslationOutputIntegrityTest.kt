package document.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TranslationOutputIntegrityTest {
    private fun paragraph(text: String) = PivotBlock.Paragraph(listOf(PivotInline.Text(text, true)))

    private val blocks =
        listOf(
            PivotBlock.Heading(2, "Section", true),
            paragraph("Bonjour le monde."),
            paragraph("Deuxieme paragraphe."),
        )

    @Test
    fun `identical structure is ok`() {
        val verdict = TranslationOutputIntegrity.verdict(blocks, blocks)

        assertIs<TranslationOutputIntegrity.Verdict.Ok>(verdict)
        assertEquals(3, verdict.blocks)
    }

    @Test
    fun `a re-inserted block is duplicated`() {
        val verdict = TranslationOutputIntegrity.verdict(blocks, blocks + blocks)

        val duplicated = assertIs<TranslationOutputIntegrity.Verdict.Duplicated>(verdict)
        assertEquals(3, duplicated.expected)
        assertEquals(6, duplicated.actual)
    }

    @Test
    fun `a dropped block is truncated`() {
        val verdict = TranslationOutputIntegrity.verdict(blocks, blocks.take(2))

        val truncated = assertIs<TranslationOutputIntegrity.Verdict.Truncated>(verdict)
        assertEquals(3, truncated.expected)
        assertEquals(2, truncated.actual)
    }

    @Test
    fun `duplication fails the translation with the article label`() {
        val exception =
            assertFailsWith<TranslationException> {
                TranslationOutputIntegrity.requireNoDuplication(blocks, blocks + blocks, "article.adoc")
            }

        val message = exception.message.orEmpty()
        assertEquals(true, message.contains("article.adoc"))
        assertEquals(true, message.contains("duplicated"))
    }

    @Test
    fun `truncation does not fail the structural guard`() {
        TranslationOutputIntegrity.requireNoDuplication(blocks, blocks.take(1), "article.adoc")
    }
}
