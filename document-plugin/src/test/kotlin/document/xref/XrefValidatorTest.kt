package document.xref

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XrefValidatorTest {

    @Test
    fun `extractAnchors finds block anchor`() {
        val anchors = XrefValidator.extractAnchors("[[intro]]Text\n")
        assertEquals(setOf("intro"), anchors)
    }

    @Test
    fun `extractAnchors finds inline anchor with label`() {
        val anchors = XrefValidator.extractAnchors("[[sec-1,Section One]]Some text\n")
        assertEquals(setOf("sec-1"), anchors)
    }

    @Test
    fun `extractAnchors finds block id shorthand`() {
        val anchors = XrefValidator.extractAnchors("[#chapter-2]\n== Chapter\n")
        assertEquals(setOf("chapter-2"), anchors)
    }

    @Test
    fun `extractAnchors finds hierarchical dotted id`() {
        val anchors = XrefValidator.extractAnchors("[[1.2.3]]Section\n")
        assertEquals(setOf("1.2.3"), anchors)
    }

    @Test
    fun `extractAnchors deduplicates`() {
        val anchors = XrefValidator.extractAnchors("[[dup]]\n[[dup]]\n[#dup]\n")
        assertEquals(setOf("dup"), anchors)
    }

    @Test
    fun `extractReferences finds angle reference`() {
        val refs = XrefValidator.extractReferences("See <<intro>> for details.\n")
        assertEquals(listOf("intro"), refs)
    }

    @Test
    fun `extractReferences finds angle reference with text`() {
        val refs = XrefValidator.extractReferences("See <<intro,Introduction>> here.\n")
        assertEquals(listOf("intro"), refs)
    }

    @Test
    fun `extractReferences finds xref macro`() {
        val refs = XrefValidator.extractReferences("xref:sec-1[] and xref:sec-2[Section Two]\n")
        assertEquals(listOf("sec-1", "sec-2"), refs)
    }

    @Test
    fun `validate returns Valid when all references resolve`() {
        val text = "[[intro]]Intro\n\nSee <<intro>> and xref:intro[Intro].\n"
        assertEquals(XrefValidationResult.Valid, XrefValidator.validate(text))
    }

    @Test
    fun `validate returns Invalid with missing references`() {
        val text = "[[intro]]Intro\n\nSee <<missing>> and <<also-missing>>.\n"
        val result = XrefValidator.validate(text)
        assertTrue(result is XrefValidationResult.Invalid)
        assertEquals(listOf("also-missing", "missing"), (result as XrefValidationResult.Invalid).missing)
    }

    @Test
    fun `validate ignores unresolved references inside code blocks heuristic`() {
        // The validator is textual; a reference-looking token inside a passthrough
        // is still matched (acceptable parallel to includeGuard). Here we assert the
        // pure behaviour : a reference without a defined anchor is reported.
        val text = "++++\n<<ghost>>\n++++\n"
        assertTrue(XrefValidator.validate(text) is XrefValidationResult.Invalid)
    }

    @Test
    fun `validate handles duplicate references to same missing anchor`() {
        val text = "<<ghost>> and <<ghost>> again\n"
        val result = XrefValidator.validate(text)
        assertTrue(result is XrefValidationResult.Invalid)
        assertEquals(listOf("ghost"), (result as XrefValidationResult.Invalid).missing)
    }
}
