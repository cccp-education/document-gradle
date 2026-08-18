package document.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ContentTranslationServiceFrontmatterRetranslateTest {

    private val fakeTranslator = FakeTranslationService(" [EN]")
    private val service = ContentTranslationService(fakeTranslator)

    @Test
    fun `retranslateFrontmatter returns false when frontmatter is not stale`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR

Corps de l article.
"""
        val targetText = """= Title FR [EN]
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR [EN]

Corps de l article EN.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertFalse(result.retranslated, "non-stale frontmatter must not be re-translated")
        assertEquals(0, result.staleKeys.size)
        assertEquals(targetText, targetFile.readText(), "target file must be unchanged when not stale")
    }

    @Test
    fun `retranslateFrontmatter retranslates stale jbake-summary preserving body`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR

Corps de l article.
"""
        val targetText = """= Title EN
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR

Body of the article in English already translated.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertTrue(result.retranslated, "stale summary must be re-translated")
        assertEquals(setOf("summary"), result.staleKeys)
        val updated = targetFile.readText()
        assertTrue(updated.contains(":jbake-summary: Resume FR [EN]"),
            "stale summary must be translated, got: ${updated.lines().find { it.contains(":jbake-summary:") }}")
        assertTrue(updated.contains("Body of the article in English already translated."),
            "body blocks must be preserved (economie d'encre), got: ${updated.substringAfter(":jbake-status: published")}")
    }

    @Test
    fun `retranslateFrontmatter retranslates stale asciidoc summary preserving body`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:summary: Resume FR sans prefixe jbake

Corps de l article.
"""
        val targetText = """= Title EN
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:summary: Resume FR sans prefixe jbake

Body of the article in English already translated.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertTrue(result.retranslated)
        assertEquals(setOf("summary"), result.staleKeys)
        val updated = targetFile.readText()
        assertTrue(updated.contains(":summary: Resume FR sans prefixe jbake [EN]"),
            "asciidoc stale summary must be translated, got: ${updated.lines().find { it.contains(":summary:") }}")
        assertTrue(updated.contains("Body of the article in English already translated."))
    }

    @Test
    fun `retranslateFrontmatter retranslates stale title preserving body`() {
        val sourceText = """= Titre FR non traduit
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published

Corps de l article.
"""
        val targetText = """= Titre FR non traduit
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published

Body already in English.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertTrue(result.retranslated)
        assertEquals(setOf("title"), result.staleKeys)
        val updated = targetFile.readText()
        assertTrue(updated.startsWith("= Titre FR non traduit [EN]"),
            "stale title must be translated, got first line: ${updated.lines().firstOrNull()}")
        assertTrue(updated.contains("Body already in English."))
    }

    @Test
    fun `retranslateFrontmatter returns false when target file does not exist`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-summary: Resume FR

Corps.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = File.createTempFile("target-nonexistent", ".adoc").apply { delete() }

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertFalse(result.retranslated, "missing target file must not be re-translated (nothing to fix)")
    }

    @Test
    fun `retranslateFrontmatter retranslates stale description preserving body`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-description: Description longue FR

Corps de l article.
"""
        val targetText = """= Title EN
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-description: Description longue FR

Body already translated.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertTrue(result.retranslated)
        assertEquals(setOf("description"), result.staleKeys)
        val updated = targetFile.readText()
        assertTrue(updated.contains(":jbake-description: Description longue FR [EN]"))
        assertTrue(updated.contains("Body already translated."))
    }

    @Test
    fun `retranslateFrontmatter retranslates multiple stale keys simultaneously`() {
        val sourceText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR
:jbake-description: Description FR

Corps de l article.
"""
        val targetText = """= Titre FR
@CherOliv
2026-01-01
:jbake-type: post
:jbake-status: published
:jbake-summary: Resume FR
:jbake-description: Description FR

Body already translated.
"""
        val sourceFile = createTempFile("source", ".adoc", sourceText)
        val targetFile = createTempFile("target", ".adoc", targetText)

        val result = service.retranslateFrontmatter(sourceFile, targetFile, "fr", "en")

        assertTrue(result.retranslated)
        assertEquals(setOf("title", "summary", "description"), result.staleKeys)
        val updated = targetFile.readText()
        assertTrue(updated.startsWith("= Titre FR [EN]"))
        assertTrue(updated.contains(":jbake-summary: Resume FR [EN]"))
        assertTrue(updated.contains(":jbake-description: Description FR [EN]"))
        assertTrue(updated.contains("Body already translated."))
    }

    private fun createTempFile(prefix: String, suffix: String, content: String): File =
        File.createTempFile(prefix, suffix).apply { writeText(content); deleteOnExit() }
}