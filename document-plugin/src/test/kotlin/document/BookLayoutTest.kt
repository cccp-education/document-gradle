package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookLayoutTest {

    // --- Matter classification (FRONT / BODY / BACK) ---

    @Test
    fun `ref starting with 0_dot is FRONT matter`() {
        assertEquals(Matter.FRONT, Matter.classify("0.1"))
    }

    @Test
    fun `deep ref starting with 0_dot is FRONT matter`() {
        assertEquals(Matter.FRONT, Matter.classify("0.5.2"))
    }

    @Test
    fun `ref starting with 9_dot is BACK matter`() {
        assertEquals(Matter.BACK, Matter.classify("9.1"))
    }

    @Test
    fun `deep ref starting with 9_dot is BACK matter`() {
        assertEquals(Matter.BACK, Matter.classify("9.0.3"))
    }

    @Test
    fun `ref starting with body prefix is BODY matter`() {
        assertEquals(Matter.BODY, Matter.classify("1.0.1"))
    }

    @Test
    fun `deep body ref is BODY matter`() {
        assertEquals(Matter.BODY, Matter.classify("2.3.4.5"))
    }

    @Test
    fun `root ref is BODY matter by default`() {
        assertEquals(Matter.BODY, Matter.classify(""))
    }

    @Test
    fun `classify honours a custom FRONT prefix`() {
        assertEquals(Matter.FRONT, Matter.classify("X.1", frontPrefix = "X"))
        assertEquals(Matter.BODY, Matter.classify("1.0.1", frontPrefix = "X"))
    }

    @Test
    fun `classify honours a custom BACK prefix`() {
        assertEquals(Matter.BACK, Matter.classify("Z.1", backPrefix = "Z"))
        assertEquals(Matter.BODY, Matter.classify("1.0.1", backPrefix = "Z"))
    }

    // --- BookLayout emission ---

    @Test
    fun `heading level 1 emits a double-equal title`() {
        assertEquals("== Introduction", BookLayout().heading(1, "Introduction"))
    }

    @Test
    fun `heading level 4 emits a five-equal title`() {
        assertEquals("===== Sous-section", BookLayout().heading(4, "Sous-section"))
    }

    @Test
    fun `page break and table of contents are emitted as AsciiDoc`() {
        val layout = BookLayout()
        assertEquals("<<<", layout.pageBreak())
        assertEquals(":toc: macro", layout.tableOfContents())
        assertEquals("toc::[]", layout.tableOfContentsBlock())
        val titlePage = layout.titlePage("Mon Livre", "Cheroliv")
        assertTrue(titlePage.startsWith("= Mon Livre"))
        assertTrue(titlePage.contains(":author: Cheroliv"))
        assertTrue(titlePage.contains(":doctype: book"))
        assertFalse(layout.heading(1, "x").contains("\n"))
    }

    // --- DOC-BOOK-IMAGES — :imagesdir: emission ---

    @Test
    fun `title page emits no imagesdir attribute by default`() {
        assertFalse(BookLayout().titlePage("Mon Livre", "Cheroliv").contains(":imagesdir:"))
    }

    @Test
    fun `title page emits the imagesdir attribute when set`() {
        val titlePage = BookLayout(imagesDir = "/tmp/scans").titlePage("Mon Livre", "Cheroliv")
        assertTrue(titlePage.contains(":imagesdir: /tmp/scans"), "the imagesdir must be emitted, got:\n$titlePage")
    }

    // --- DOC-BOOK-MATTER — matter separators & dedicated title page ---

    @Test
    fun `matter break is emitted as an AsciiDoc hard page break`() {
        assertEquals("<<<", BookLayout().matterBreak())
    }

    @Test
    fun `matter breaks and a dedicated title page are off by default`() {
        val layout = BookLayout()
        assertFalse(layout.emitMatterBreaks, "matter breaks must be opt-in (non-regression)")
        assertEquals(MatterPolicy.DEFAULT, layout.matterPolicy, "the default policy keeps the 0/9 convention")
    }

    @Test
    fun `an explicit matter policy can be injected in the layout`() {
        val layout = BookLayout(matterPolicy = MatterPolicy.NONE)
        assertEquals(Matter.BODY, layout.matterPolicy.classify("0.1"))
    }
}
