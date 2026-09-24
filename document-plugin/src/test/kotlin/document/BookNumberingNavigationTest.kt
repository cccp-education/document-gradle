package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DOC-BOOK-CONSISTENCY-B6 — navigation must run over the *emitted* sections
 * (one heading per TOC row), not over the raw OCR pages.
 *
 * A multi-page TOC row (`| 1.0.1 | … | 5, 6, 7, 8 | …`) expands into several
 * [BookSection]s sharing the same `ref`; navigating the raw leaves then points
 * "next" at the same logical section. The navigation of a node is the next
 * *distinct* section, in document order.
 */
class BookNumberingNavigationTest {

    private fun multiPageTree(): BookTree {
        val sections = listOf(
            BookSection(ref = "1.0.1", title = "First", page = 5, pdfFile = "005.adoc"),
            BookSection(ref = "1.0.1", title = "First", page = 6, pdfFile = "006.adoc"),
            BookSection(ref = "1.0.1", title = "First", page = 7, pdfFile = "007.adoc"),
            BookSection(ref = "1.0.1", title = "First", page = 8, pdfFile = "008.adoc"),
            BookSection(ref = "1.0.2", title = "Second", page = 14, pdfFile = "014.adoc"),
        )
        return BookTreeBuilder.fromSections(sections)
    }

    @Test
    fun `navigation of a multi-page section points to the next distinct section`() {
        val tree = multiPageTree()
        val nav = BookNumbering.navigation(tree, "1.0.1")
        assertNull(nav.previous, "the first section has no previous")
        assertNotNull(nav.next, "the first section must have a next")
        assertEquals("1.0.2", nav.next?.ref, "next must be the next distinct section, not the same ref")
    }

    @Test
    fun `the last distinct section has no next even when it spans several pages`() {
        val tree = multiPageTree()
        val nav = BookNumbering.navigation(tree, "1.0.2")
        assertNotNull(nav.previous, "the last section must have a previous")
        assertEquals("1.0.1", nav.previous?.ref, "previous must be the previous distinct section")
        assertNull(nav.next, "the last distinct section has no next")
    }

    @Test
    fun `emitted nodes are the distinct sections in document order`() {
        val tree = multiPageTree()
        val refs = BookNumbering.emittedNodes(tree).map { it.ref }
        assertEquals(listOf("1.0.1", "1.0.2"), refs)
    }

    // --- DOC-BOOK-CONSISTENCY-B6 — BookLayout navigation links ---

    @Test
    fun `navigation is off by default (non-regression)`() {
        assertFalse(BookLayout().emitNavigation, "navigation links must be opt-in")
    }

    @Test
    fun `navigation links emit a previous and a next cross-reference`() {
        val layout = BookLayout(emitNavigation = true)
        val nav = BookNavigation(
            previous = BookSection(ref = "1.0.1", title = "First", page = 5, pdfFile = "005.adoc"),
            next = BookSection(ref = "1.0.3", title = "Third", page = 14, pdfFile = "014.adoc"),
        )
        val links = layout.navigationLinks(nav)
        assertTrue(links.contains("<<1.0.1,First>>"), "the previous section must be linked, got:\n$links")
        assertTrue(links.contains("<<1.0.3,Third>>"), "the next section must be linked, got:\n$links")
    }

    @Test
    fun `navigation links omit a missing side at the book boundaries`() {
        val layout = BookLayout(emitNavigation = true)
        val onlyNext = BookNavigation(
            previous = null,
            next = BookSection(ref = "1.0.2", title = "Second", page = 14, pdfFile = "014.adoc"),
        )
        val links = layout.navigationLinks(onlyNext)
        assertTrue(links.contains("<<1.0.2,Second>>"))
        assertEquals("<<1.0.2,Second>>", links, "only the next link must be emitted at the first boundary")

        val none = BookNavigation(previous = null, next = null)
        assertEquals("", layout.navigationLinks(none), "no links for a standalone section")
    }
}
