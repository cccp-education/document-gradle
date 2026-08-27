package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class BookNumberingTest {

    private fun sampleTree(): BookTree {
        val sections = listOf(
            BookSection(ref = "1", title = "Part I", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1.1", title = "Chapter 1", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "1.2", title = "Chapter 2", page = 3, pdfFile = "003.adoc"),
            BookSection(ref = "1.2.1", title = "Sub-section", page = 4, pdfFile = "004.adoc"),
            BookSection(ref = "2", title = "Part II", page = 5, pdfFile = "005.adoc"),
        )
        return BookTreeBuilder.fromSections(sections)
    }

    // --- Hierarchical numbering ---

    @Test
    fun `top level node is numbered 1`() {
        val tree = sampleTree()
        assertEquals("1", BookNumbering.number(tree, "1"))
    }

    @Test
    fun `second top level node is numbered 2`() {
        val tree = sampleTree()
        assertEquals("2", BookNumbering.number(tree, "2"))
    }

    @Test
    fun `nested child under first part is numbered 1-dot-1`() {
        val tree = sampleTree()
        assertEquals("1.1", BookNumbering.number(tree, "1.1"))
    }

    @Test
    fun `deeply nested child is numbered 1-dot-2-dot-1`() {
        val tree = sampleTree()
        assertEquals("1.2.1", BookNumbering.number(tree, "1.2.1"))
    }

    @Test
    fun `node numbering equals the source table-of-contents ref`() {
        val sections = listOf(
            BookSection(ref = "1", title = "Part I", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "2", title = "Part II", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "2.3", title = "Chap 3", page = 3, pdfFile = "003.adoc"),
        )
        val tree2 = BookTreeBuilder.fromSections(sections)
        // the hierarchical number is the ref itself (FPA TOC convention)
        assertEquals("2.3", BookNumbering.number(tree2, "2.3"))
    }

    @Test
    fun `number returns null for unknown ref`() {
        val tree = sampleTree()
        assertNull(BookNumbering.number(tree, "9.9.9"))
    }

    @Test
    fun `number returns null for the synthetic root`() {
        val tree = sampleTree()
        assertNull(BookNumbering.number(tree, ""))
    }

    // --- Anchors ---

    @Test
    fun `anchor emits an AsciiDoc cross-reference for the ref`() {
        assertEquals("[[1.0.2]]", BookNumbering.anchor("1.0.2"))
    }

    // --- Navigation (previous / next leaf sections) ---

    @Test
    fun `navigation of a middle leaf exposes previous and next sections`() {
        val tree = sampleTree()
        val nav = BookNumbering.navigation(tree, "1.2")
        assertNotNull(nav.previous)
        assertEquals("1.1", nav.previous?.ref)
        assertNotNull(nav.next)
        assertEquals("1.2.1", nav.next?.ref)
    }

    @Test
    fun `navigation of the first leaf has no previous and the last leaf has no next`() {
        val tree = sampleTree()
        val first = BookNumbering.navigation(tree, "1")
        assertNull(first.previous)
        assertNotNull(first.next)

        val last = BookNumbering.navigation(tree, "2")
        assertNotNull(last.previous)
        assertNull(last.next)
    }
}
