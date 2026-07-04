package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentOutputsTest {

    @Test
    fun `DocumentOutputs default has only html true`() {
        val outputs = DocumentOutputs()
        assertTrue(outputs.html)
        assertFalse(outputs.pdf)
        assertFalse(outputs.epub)
        assertFalse(outputs.docbook)
        assertFalse(outputs.manpage)
    }

    @Test
    fun `DocumentOutputs enabledFormats returns only enabled formats`() {
        val outputs = DocumentOutputs(html = true, pdf = true, epub = false, docbook = false, manpage = true)
        val formats = outputs.enabledFormats()
        assertEquals(3, formats.size)
        assertTrue(formats.contains(DocumentFormat.HTML))
        assertTrue(formats.contains(DocumentFormat.PDF))
        assertTrue(formats.contains(DocumentFormat.MANPAGE))
        assertFalse(formats.contains(DocumentFormat.EPUB))
        assertFalse(formats.contains(DocumentFormat.DOCBOOK))
    }

    @Test
    fun `DocumentOutputs enabledFormats returns empty when all flags false`() {
        val outputs = DocumentOutputs(
            html = false, pdf = false, epub = false, docbook = false, manpage = false,
        )
        assertTrue(outputs.enabledFormats().isEmpty())
    }

    @Test
    fun `DocumentOutputs all returns all five formats enabled`() {
        val outputs = DocumentOutputs(
            html = true, pdf = true, epub = true, docbook = true, manpage = true,
        )
        assertEquals(5, outputs.enabledFormats().size)
    }
}