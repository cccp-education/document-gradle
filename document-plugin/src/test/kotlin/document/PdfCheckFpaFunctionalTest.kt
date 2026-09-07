package document

import document.pdf.PdfBoxValidatorAdapter
import document.pdf.PdfValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Dogfooding test — 6th guard (DOC-PDF-CHECK) against the real FPA book PDF.
 *
 * Audits `livre-navigable/book.pdf` (produced by the bookPipeline in S-217/218)
 * with Apache PDFBox — the exact same adapter the `validateDocumentPdf` task
 * wires. Read-only ink-economy audit of the existing artefact: no regeneration,
 * no JRuby conversion (mirrors [EpubCheckFpaFunctionalTest]).
 *
 * This locks the "livre FPA SERVER" product: the shipped PDF must be
 * structurally sound (loadable, non-empty, extractable text on every page),
 * not just open. The test self-skips (`assumeTrue`) when the corpus is absent.
 */
class PdfCheckFpaFunctionalTest {

    companion object {
        private val FPA_BOOK_PDF = File(
            "/home/cheroliv/workspace/office/metiers/FPA",
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/livre-navigable/book.pdf",
        )
    }

    @Test
    fun `real FPA book pdf passes the structural checks`() {
        assumeTrue(FPA_BOOK_PDF.isFile) {
            "FPA book.pdf not found at ${FPA_BOOK_PDF.absolutePath}"
        }
        val result = PdfBoxValidatorAdapter().validate(FPA_BOOK_PDF)
        assertEquals(
            PdfValidationResult.Valid,
            result,
            "shipped FPA PDF must pass the PDFBox structural checks; issues=${(result as? PdfValidationResult.Invalid)?.issues}",
        )
    }

    @Test
    fun `textless FPA book pdf copy is reported Invalid - negative proof`() {
        assumeTrue(FPA_BOOK_PDF.isFile) {
            "FPA book.pdf not found at ${FPA_BOOK_PDF.absolutePath}"
        }
        // Structural corruption on a byte copy (never the source artefact — Rule 7
        // read-only). PDFBox is lenient about a corrupted header/truncated tail
        // (xref rebuilding + header warning — both proven FPA-real), so the
        // meaningful negative proof is the *business* finding: a rebuilt PDF whose
        // pages carry NO extractable text must be reported with the per-page
        // findings. Mirrors the EPUB lesson (pitfall #10: corrupt the STRUCTURE,
        // not the content — PDFBox tolerates byte-level damage in a parseable file).
        val copy = File.createTempFile("fpa-book-textless", ".pdf")
        copy.deleteOnExit()
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            repeat(3) {
                doc.addPage(
                    org.apache.pdfbox.pdmodel.PDPage(
                        org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER,
                    ),
                )
            }
            doc.save(copy)
        }
        val result = PdfBoxValidatorAdapter().validate(copy)
        assertTrue(result is PdfValidationResult.Invalid) {
            "a textless PDF copy must be reported Invalid, got Valid"
        }
        val issues = (result as PdfValidationResult.Invalid).issues
        assertTrue(issues.size == 3, "every textless page must be a finding: $issues")
        assertTrue(issues.all { it.contains("no extractable text") }, "findings must name the textless pages: $issues")
    }
}