package document.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [PdfBoxValidatorAdapter] (US-1, EPIC DOC-PDF-CHECK) —
 * characterization tests of the real PDFBox bridge. The valid-PDF fixture is a
 * minimal hand-built PDF (PDDocument + PDPage + extractable text) — no
 * versioned binary, no AsciidoctorJ spin-up (Ink Economy Law).
 */
class PdfBoxValidatorAdapterTest {

    private val adapter = PdfBoxValidatorAdapter()

    @TempDir
    lateinit var tmp: File

    @Test
    fun `missing file is Invalid with the pdf-file-missing marker`() {
        val result = adapter.validate(File(tmp, "absent.pdf"))

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<pdf-file-missing>")
        assertThat(result.issues.single()).contains("absent.pdf")
    }

    @Test
    fun `a directory is Invalid with the pdf-file-missing marker`() {
        val dir = File(tmp, "not-a-file.pdf").apply { mkdirs() }

        val result = adapter.validate(dir)

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<pdf-file-missing>")
    }

    @Test
    fun `a non-pdf file is Invalid with the pdf-load-failed marker`() {
        val fake = File(tmp, "fake.pdf").apply { writeText("this is not a pdf document") }

        val result = adapter.validate(fake)

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<pdf-load-failed>")
    }

    @Test
    fun `a minimal one-page PDF with extractable text is Valid`() {
        val result = adapter.validate(minimalPdfFile(pages = 1, textPerPage = true))

        assertThat(result).isEqualTo(PdfValidationResult.Valid)
    }

    @Test
    fun `a zero-page PDF is Invalid with the pdf-no-pages marker`() {
        val result = adapter.validate(minimalPdfFile(pages = 0, textPerPage = false))

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("<pdf-no-pages>")
    }

    @Test
    fun `a page without extractable text is reported as a finding`() {
        val result = adapter.validate(minimalPdfFile(pages = 2, textPerPage = false))

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues).hasSize(2)
        assertThat(result.issues[0]).contains("page 1: no extractable text")
        assertThat(result.issues[1]).contains("page 2: no extractable text")
    }

    @Test
    fun `only textless pages are reported in a mixed document`() {
        val pdf = File(tmp, "mixed.pdf")
        buildPdf(pdf) { doc ->
            addPageWithText(doc, "I have text.")
            addBlankPage(doc)
        }

        val result = adapter.validate(pdf)

        assertThat(result).isInstanceOf(PdfValidationResult.Invalid::class.java)
        assertThat(result.issues.single()).contains("page 2: no extractable text")
    }

    @Test
    fun `adapter is reusable across calls (stateless)`() {
        val pdf = minimalPdfFile(pages = 1, textPerPage = true)

        adapter.validate(pdf)
        val second = adapter.validate(pdf)

        assertThat(second).isEqualTo(PdfValidationResult.Valid)
    }

    /** Builds a minimal PDF with [pages] pages, each either blank or carrying text. */
    private fun minimalPdfFile(pages: Int, textPerPage: Boolean): File {
        val file = File(tmp, "minimal-${pages}-${textPerPage}.pdf")
        buildPdf(file) { doc ->
            repeat(pages) { index ->
                if (textPerPage) {
                    addPageWithText(doc, "Page ${index + 1} content.")
                } else {
                    addBlankPage(doc)
                }
            }
        }
        return file
    }

    private fun buildPdf(file: File, block: (PDDocument) -> Unit) {
        PDDocument().use { doc ->
            block(doc)
            doc.save(file)
        }
    }

    private fun addPageWithText(
        doc: PDDocument,
        text: String,
    ) {
        val page = PDPage(PDRectangle.LETTER)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font(FontName.HELVETICA), 12f)
            stream.newLineAtOffset(50f, 700f)
            stream.showText(text)
            stream.endText()
        }
    }

    private fun addBlankPage(doc: PDDocument) {
        doc.addPage(PDPage(PDRectangle.LETTER))
    }
}