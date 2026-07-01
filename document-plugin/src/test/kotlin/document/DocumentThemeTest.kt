package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class DocumentThemeTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-theme").toFile()

    private fun file(dir: File, name: String, content: String = ""): File =
        File(dir, name).apply { writeText(content) }

    @Test
    fun `DocumentTheme default has no theme files configured`() {
        val theme = DocumentTheme()

        assertNull(theme.pdfTheme)
        assertNull(theme.htmlStylesheet)
        assertNull(theme.epubStylesheet)
        assertNull(theme.logo)
    }

    @Test
    fun `DocumentTheme stores pdfTheme file when provided`() {
        val dir = tempDir()
        val yml = file(dir, "talaria-theme.yml", "extends: default\n")

        val theme = DocumentTheme(pdfTheme = yml)

        assertEquals(yml, theme.pdfTheme)
    }

    @Test
    fun `DocumentTheme stores htmlStylesheet file when provided`() {
        val dir = tempDir()
        val css = file(dir, "talaria.css", "body { font-family: serif; }")

        val theme = DocumentTheme(htmlStylesheet = css)

        assertEquals(css, theme.htmlStylesheet)
    }

    @Test
    fun `DocumentTheme stores epubStylesheet file when provided`() {
        val dir = tempDir()
        val css = file(dir, "epub.css", "body { margin: 0; }")

        val theme = DocumentTheme(epubStylesheet = css)

        assertEquals(css, theme.epubStylesheet)
    }

    @Test
    fun `DocumentTheme stores logo file when provided`() {
        val dir = tempDir()
        val logo = File(dir, "logo.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }

        val theme = DocumentTheme(logo = logo)

        assertEquals(logo, theme.logo)
    }

    @Test
    fun `DocumentTheme isEmpty is true when no theme files are set`() {
        val theme = DocumentTheme()

        assertTrue(theme.isEmpty())
    }

    @Test
    fun `DocumentTheme isEmpty is false when at least one theme file is set`() {
        val dir = tempDir()
        val css = file(dir, "talaria.css", "body {}")

        val theme = DocumentTheme(htmlStylesheet = css)

        assertTrue(!theme.isEmpty())
    }

    @Test
    fun `DocumentTheme toAttributes injects stylesheet for HTML when htmlStylesheet is set`() {
        val dir = tempDir()
        val css = file(dir, "talaria.css", "body {}")

        val attrs = DocumentTheme(htmlStylesheet = css).toAttributes(DocumentFormat.HTML)

        assertEquals(css.absolutePath, attrs["stylesheet"])
    }

    @Test
    fun `DocumentTheme toAttributes injects pdf-theme for PDF when pdfTheme is set`() {
        val dir = tempDir()
        val yml = file(dir, "talaria-theme.yml", "extends: default\n")

        val attrs = DocumentTheme(pdfTheme = yml).toAttributes(DocumentFormat.PDF)

        assertEquals(yml.absolutePath, attrs["pdf-theme"])
    }

    @Test
    fun `DocumentTheme toAttributes injects epub-stylesheet for EPUB when epubStylesheet is set`() {
        val dir = tempDir()
        val css = file(dir, "epub.css", "body {}")

        val attrs = DocumentTheme(epubStylesheet = css).toAttributes(DocumentFormat.EPUB)

        assertEquals(css.absolutePath, attrs["epub-stylesheet"])
    }

    @Test
    fun `DocumentTheme toAttributes injects pdf-theme and logo for PDF when both are set`() {
        val dir = tempDir()
        val yml = file(dir, "talaria-theme.yml", "extends: default\n")
        val logo = File(dir, "logo.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }

        val attrs = DocumentTheme(pdfTheme = yml, logo = logo).toAttributes(DocumentFormat.PDF)

        assertEquals(yml.absolutePath, attrs["pdf-theme"])
        assertEquals(logo.absolutePath, attrs["pdf-front-cover-image"])
    }

    @Test
    fun `DocumentTheme toAttributes is empty for HTML when no theme is set`() {
        val attrs = DocumentTheme().toAttributes(DocumentFormat.HTML)

        assertTrue(attrs.isEmpty())
    }

    @Test
    fun `DocumentTheme toAttributes is empty for PDF when no theme is set`() {
        val attrs = DocumentTheme().toAttributes(DocumentFormat.PDF)

        assertTrue(attrs.isEmpty())
    }

    @Test
    fun `DocumentTheme toAttributes ignores htmlStylesheet for PDF format`() {
        val dir = tempDir()
        val css = file(dir, "talaria.css", "body {}")

        val attrs = DocumentTheme(htmlStylesheet = css).toAttributes(DocumentFormat.PDF)

        assertTrue(!attrs.containsKey("stylesheet"))
    }

    @Test
    fun `DocumentTheme toAttributes ignores pdfTheme for HTML format`() {
        val dir = tempDir()
        val yml = file(dir, "talaria-theme.yml", "extends: default\n")

        val attrs = DocumentTheme(pdfTheme = yml).toAttributes(DocumentFormat.HTML)

        assertTrue(!attrs.containsKey("pdf-theme"))
    }

    @Test
    fun `DocumentTheme equals and hashCode are based on theme files`() {
        val dir = tempDir()
        val css = file(dir, "talaria.css", "body {}")
        val yml = file(dir, "talaria-theme.yml", "extends: default\n")

        val theme1 = DocumentTheme(pdfTheme = yml, htmlStylesheet = css)
        val theme2 = DocumentTheme(pdfTheme = yml, htmlStylesheet = css)

        assertEquals(theme1, theme2)
        assertEquals(theme1.hashCode(), theme2.hashCode())
    }
}