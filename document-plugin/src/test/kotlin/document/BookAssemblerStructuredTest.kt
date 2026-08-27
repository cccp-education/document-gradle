package document

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import java.nio.file.Files

class BookAssemblerStructuredTest {

    private fun treeWithFrontBodyBack(): BookTree {
        val sections = listOf(
            BookSection(ref = "0.1", title = "Preface", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "1.1", title = "Chapter 1", page = 3, pdfFile = "003.adoc"),
            BookSection(ref = "1.2", title = "Chapter 2", page = 4, pdfFile = "004.adoc"),
            BookSection(ref = "9.1", title = "Glossary", page = 5, pdfFile = "005.adoc"),
        )
        return BookTreeBuilder.fromSections(sections)
    }

    private fun resolver(): (BookSection) -> String = { section -> "Body of ${section.ref}" }

    @Test
    fun `structured book emits a title page with author and doctype book`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.startsWith("= My Book"), "title page must start the book")
        assertTrue(content.contains(":author: Cheroliv"), "author attribute must be present")
        assertTrue(content.contains(":doctype: book"), "doctype must be book")
    }

    @Test
    fun `structured book emits the table of contents by default`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.contains(":toc:"), "the table of contents attribute must be emitted")
    }

    @Test
    fun `level 1 node emits a double-equal heading and level 2 a triple-equal heading`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.contains("== 1. Part I"), "part must be a level-1 heading")
        assertTrue(content.contains("=== 1.1. Chapter 1"), "chapter must be a level-2 heading")
    }

    @Test
    fun `each section carries a cross-reference anchor built from its ref`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.contains("[[1.1]]"), "chapter anchor must be emitted")
        assertTrue(content.contains("[[9.1]]"), "glossary anchor must be emitted")
    }

    @Test
    fun `front body and back matter are emitted in document order`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        val preface = content.indexOf("Preface")
        val part = content.indexOf("Part I")
        val glossary = content.indexOf("Glossary")
        assertTrue(preface < part, "front matter must precede body")
        assertTrue(part < glossary, "body must precede back matter")
    }

    @Test
    fun `resolved page content is injected under the matching heading`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.contains("Body of 1.1"), "chapter page content must be present")
        assertTrue(content.contains("Body of 9.1"), "glossary page content must be present")
    }

    @Test
    fun `page break is inserted between top-level nodes by default`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertTrue(content.contains("<<<"), "a hard page break must separate top-level nodes")
    }

    @Test
    fun `page break is omitted when the layout disables it`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(pageBreakBetweenNodes = false),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertFalse(content.contains("<<<"), "no page break must be emitted when disabled")
    }

    @Test
    fun `multi-page section concatenates every physical page in order`() {
        val sections = listOf(
            BookSection(ref = "1", title = "Part I", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1.1", title = "Chapter 1", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "1.1", title = "Chapter 1", page = 3, pdfFile = "003.adoc"),
        )
        val tree = BookTreeBuilder.fromSections(sections)
        val byPage = mapOf(2 to "First page text", 3 to "Second page text")
        val content = BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = { section -> byPage[section.page] ?: "" },
        ).content

        val first = content.indexOf("First page text")
        val second = content.indexOf("Second page text")
        assertTrue(first >= 0 && second >= 0, "both physical pages must be present")
        assertTrue(first < second, "pages must be concatenated in physical order")
    }

    @Test
    fun `structured book with no title page still emits the body`() {
        val content = BookAssembler.assemble(
            tree = treeWithFrontBodyBack(),
            layout = BookLayout(emitTitlePage = false),
            title = "My Book",
            author = "Cheroliv",
            resolveContent = resolver(),
        ).content

        assertFalse(content.startsWith("= My Book"), "title page must be skipped")
        assertTrue(content.contains("== 1. Part I"), "the structured body must still be emitted")
    }

    @Test
    fun `file-backed TOC and pages are assembled into a structured navigable book`() {
        val dir = Files.createTempDirectory("doc-book-structured").toFile()
        val pagesDir = File(dir, "pages").apply { mkdirs() }
        File(pagesDir, "001-preface.adoc").writeText("== Preface\n\nFront matter content.")
        File(pagesDir, "002-part.adoc").writeText("== Part I\n\nPart content.")
        File(pagesDir, "003-chapter.adoc").writeText("=== Chapter 1\n\nChapter content.")

        val toc = File(dir, "toc.adoc").apply {
            writeText(
                """
                | Référence | Sujet / Titre | Page | Fichier
                | 0.1 | Preface | 1 | 001-preface.adoc
                | 1 | Part I | 2 | 002-part.adoc
                | 1.1 | Chapter 1 | 3 | 003-chapter.adoc
                """.trimIndent(),
            )
        }

        val sections = BookTocParser.parse(toc)
        val tree = BookTreeBuilder.fromSections(sections)
        val content = BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(),
            title = "FPA Book",
            author = "Cheroliv",
            resolveContent = BookAssembler.pageContentResolver(pagesDir),
        ).content

        assertTrue(content.contains("= FPA Book"), "title page must be present")
        assertTrue(content.contains("=== 0.1. Preface"), "front matter must be a level-2 heading")
        assertTrue(content.contains("== 1. Part I"), "part must be a level-1 heading")
        assertTrue(content.contains("=== 1.1. Chapter 1"), "chapter must be a level-2 heading")
        assertTrue(content.contains("Front matter content."), "preface page text must be resolved from disk")
        assertTrue(content.contains("Chapter content."), "chapter page text must be resolved from disk")
        assertTrue(content.contains("[[1.1]]"), "chapter anchor must be emitted")
    }
}
