package document.epub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for [LibEpubCheckAdapter] (US-1, EPIC DOC-EPUBCHECK) — characterization
 * tests of the real `org.w3c:epubcheck` bridge. The valid-EPUB fixture is a minimal
 * hand-built EPUB3 (mimetype + container.xml + OPF + nav + chapter) — no versioned
 * binary, no AsciidoctorJ spin-up (Ink Economy Law).
 */
class LibEpubCheckAdapterTest {

    private val adapter = LibEpubCheckAdapter()

    @TempDir
    lateinit var tmp: File

    @Test
    fun `missing file is Invalid with the epub-file-missing marker`() {
        val result = adapter.validate(File(tmp, "absent.epub"))

        assertThat(result).isInstanceOf(EpubValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<epub-file-missing>")
        assertThat(result.issues.single()).contains("absent.epub")
    }

    @Test
    fun `a directory is Invalid with the epub-file-missing marker`() {
        val dir = File(tmp, "not-a-file.epub").apply { mkdirs() }

        val result = adapter.validate(dir)

        assertThat(result).isInstanceOf(EpubValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<epub-file-missing>")
    }

    @Test
    fun `a non-epub file is Invalid with epubcheck issues`() {
        // A non-zero-byte file that is not a zip: epubcheck must fail it
        // (either as report messages or as a caught exception — never a throw).
        val fake = File(tmp, "fake.epub").apply { writeText("this is not a zip archive") }

        val result = adapter.validate(fake)

        assertThat(result).isInstanceOf(EpubValidationResult.Invalid::class.java)
        assertThat(result.issues).isNotEmpty
    }

    @Test
    fun `a minimal valid EPUB3 is Valid`() {
        val result = adapter.validate(minimalEpubFile())

        assertThat(result).isEqualTo(EpubValidationResult.Valid)
    }

    @Test
    fun `adapter is reusable across calls (stateless)`() {
        val epub = minimalEpubFile()

        adapter.validate(epub)
        val second = adapter.validate(epub)

        assertThat(second).isEqualTo(EpubValidationResult.Valid)
    }

    /** Builds a minimal valid EPUB3 (EPUB 3.0): mimetype first + container.xml + OPF + nav + chapter. */
    private fun minimalEpubFile(): File {
        val mimetype = "application/epub+zip".toByteArray()
        val container = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="EPUB/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent().toByteArray()
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="pub-id">urn:uuid:12345678-1234-1234-1234-123456789abc</dc:identifier>
                <dc:title>Fixture</dc:title>
                <dc:language>en</dc:language>
                <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
              </spine>
            </package>
        """.trimIndent().toByteArray()
        val nav = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" epub:type="frontmatter toc">
              <head><title>TOC</title></head>
              <body>
                <nav epub:type="toc"><ol><li><a href="ch1.xhtml">Chapter 1</a></li></ol></nav>
              </body>
            </html>
        """.trimIndent().toByteArray()
        val ch1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>Chapter 1</title></head>
              <body><h1>Chapter 1</h1><p>Hello.</p></body>
            </html>
        """.trimIndent().toByteArray()

        val file = File(tmp, "minimal.epub")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // The mimetype entry must be the FIRST and STORED (uncompressed).
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(container)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("EPUB/content.opf"))
            zip.write(opf)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("EPUB/nav.xhtml"))
            zip.write(nav)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("EPUB/ch1.xhtml"))
            zip.write(ch1)
            zip.closeEntry()
        }
        return file
    }
}