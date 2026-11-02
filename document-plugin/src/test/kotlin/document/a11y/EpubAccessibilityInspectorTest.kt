package document.a11y

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DOC-A11Y — the producer-level EPUB checks.
 *
 * The EPUB is read as a ZIP: every XHTML content document is checked for the
 * structural signals, and the OPF for the EPUB 3 accessibility metadata.
 */
class EpubAccessibilityInspectorTest {
    @TempDir
    lateinit var dir: File

    private fun epub(
        xhtml: String,
        opf: String = """<package><metadata><meta property="schema:accessMode">textual</meta></metadata></package>""",
    ): File {
        val file = File(dir, "book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun write(
                name: String,
                content: String,
            ) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            write("mimetype", "application/epub+zip")
            write("META-INF/container.xml", "<container/>")
            write("OEBPS/content.opf", opf)
            write("OEBPS/chapter.xhtml", xhtml)
        }
        return file
    }

    @Test
    fun `a well-formed EPUB has no finding`() {
        val findings =
            EpubAccessibilityInspector.inspect(
                epub("""<html lang="fr"><body><h1>T</h1><img src="a.png" alt="page"/></body></html>"""),
                "book.epub",
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a missing lang is an error`() {
        val findings =
            EpubAccessibilityInspector.inspect(
                epub("<html><body><h1>T</h1></body></html>"),
                "book.epub",
            )

        assertTrue(findings.any { it.rule == DocumentA11yRules.EPUB_LANG_MISSING && it.severity == DocumentA11ySeverity.ERROR })
    }

    @Test
    fun `an image without alt is an error`() {
        val findings =
            EpubAccessibilityInspector.inspect(
                epub("""<html lang="fr"><body><h1>T</h1><img src="a.png"></body></html>"""),
                "book.epub",
            )

        assertTrue(findings.any { it.rule == DocumentA11yRules.EPUB_IMAGE_ALT_MISSING })
    }

    @Test
    fun `missing accessibility metadata is a warning`() {
        val findings =
            EpubAccessibilityInspector.inspect(
                epub("""<html lang="fr"><body><h1>T</h1></body></html>""", opf = "<package><metadata/></package>"),
                "book.epub",
            )

        assertTrue(findings.any { it.rule == DocumentA11yRules.EPUB_ACCESSIBILITY_METADATA_MISSING })
    }
}
