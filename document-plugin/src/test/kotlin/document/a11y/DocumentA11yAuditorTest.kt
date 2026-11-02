package document.a11y

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DOC-A11Y — the artifacts-directory audit. */
class DocumentA11yAuditorTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `an absent directory yields an empty report`() {
        assertTrue(DocumentA11yAuditor.audit(null).deliverables.isEmpty())
    }

    @Test
    fun `an html and a pdf are both reported`() {
        File(dir, "document.html").writeText("<html lang=\"fr\"><body><h1>T</h1></body></html>")
        File(dir, "document.pdf").writeText("%PDF-1.7")

        val report = DocumentA11yAuditor.audit(dir)

        assertEquals(2, report.deliverables.size)
        assertTrue(report.deliverables.any { it.kind == "HTML" })
        val pdf = report.deliverables.first { it.kind == "PDF" }
        assertTrue(pdf.findings.any { it.rule == DocumentA11yRules.PDF_NOT_TAGGED })
        assertTrue(pdf.limits.isNotEmpty())
    }

    @Test
    fun `a degraded html carries its findings`() {
        File(dir, "document.html").writeText("<html><body><h2>T</h2></body></html>")

        val report = DocumentA11yAuditor.audit(dir)

        val html = report.deliverables.first { it.kind == "HTML" }
        assertTrue(html.findings.any { it.rule == DocumentA11yRules.HTML_LANG_MISSING })
    }
}
