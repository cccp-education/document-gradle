package document.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Adapter bridging the real Apache PDFBox library (3.x, BOM-constrained) to
 * the [PdfValidator] port (DOC-PDF-CHECK).
 *
 * Structural checks, in order:
 * 1. file exists and is a regular file — else `<pdf-file-missing>` ;
 * 2. the binary loads (PDFBox header + parse) — else `<pdf-load-failed>` ;
 * 3. the document has at least one page — else `<pdf-no-pages>` ;
 * 4. every page carries extractable text (PDFTextStripper per-page) — else
 *    the finding `page N: no extractable text`.
 *
 * Findings are sorted and deduplicated (mirror of the epub adapter). Any
 * PDFBox exception is caught as an [PdfValidationResult.Invalid], never
 * propagated — the port contract. A missing/unreadable file is a finding,
 * not an exception.
 */
class PdfBoxValidatorAdapter : PdfValidator {

    override fun validate(file: File): PdfValidationResult {
        if (!file.exists() || !file.isFile) {
            return PdfValidationResult.Invalid(listOf("<pdf-file-missing> ${file.name}"))
        }
        val document = try {
            Loader.loadPDF(file)
        } catch (e: InvalidPasswordException) {
            return PdfValidationResult.Invalid(
                listOf("<pdf-load-failed> encrypted PDF: ${e.message}"),
            )
        } catch (e: Exception) {
            return PdfValidationResult.Invalid(
                listOf("<pdf-load-failed> ${e.javaClass.simpleName}: ${e.message}"),
            )
        }
        document.use { doc ->
            if (doc.numberOfPages == 0) {
                return PdfValidationResult.Invalid(listOf("<pdf-no-pages>"))
            }
            val issues = mutableListOf<String>()
            for (page in 1..doc.numberOfPages) {
                val text = textOf(doc, page)
                if (text.isBlank()) {
                    issues += "page $page: no extractable text"
                }
            }
            return if (issues.isEmpty()) {
                PdfValidationResult.Valid
            } else {
                PdfValidationResult.Invalid(issues.distinct().sorted())
            }
        }
    }

    /** Extracts the text of a single 1-based page (startPage/endPage are 0-based). */
    private fun textOf(doc: PDDocument, page: Int): String = try {
        PDFTextStripper().apply {
            startPage = page
            endPage = page
        }.getText(doc).trim()
    } catch (e: Exception) {
        // A page that cannot be stripped is textless for audit purposes.
        ""
    }
}