package document.validation

import document.security.IncludeGuardMode
import document.xref.XrefValidationMode
import org.asciidoctor.SafeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentValidatorTest {

    private val baseDir = File("src/test/resources/validation").apply { mkdirs() }

    @Test
    fun `valid source yields VALID everywhere`() {
        val text = "[[intro]]\n== Intro\n\nSee <<intro>> for details.\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.OFF, XrefValidationMode.OFF, SafeMode.UNSAFE,
        )
        assertEquals("OFF", report.includeGuard.status)
        assertEquals(null, report.xref)
        assertEquals("VALID", report.security.advice)
    }

    @Test
    fun `include guard absolute path is INVALID under STRICT`() {
        val text = "include::/etc/passwd[]\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.STRICT, XrefValidationMode.OFF, SafeMode.UNSAFE,
        )
        assertEquals("INVALID", report.includeGuard.status)
        assertEquals("/etc/passwd", report.includeGuard.offendingTarget)
    }

    @Test
    fun `include guard absolute path is reported but not failing under LENIENT`() {
        val text = "include::/etc/passwd[]\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.LENIENT, XrefValidationMode.OFF, SafeMode.UNSAFE,
        )
        assertEquals("INVALID", report.includeGuard.status)
    }

    @Test
    fun `xref missing reference is MISSING under STRICT`() {
        val text = "See <<missing>> for details.\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.OFF, XrefValidationMode.STRICT, SafeMode.UNSAFE,
        )
        assertEquals("MISSING", report.xref?.status)
        assertTrue(report.xref?.missing?.contains("missing") == true)
    }

    @Test
    fun `xref validation skipped when mode OFF`() {
        val text = "See <<missing>> for details.\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.OFF, XrefValidationMode.OFF, SafeMode.UNSAFE,
        )
        assertEquals(null, report.xref)
    }

    @Test
    fun `security advice REJECT when STRICT include guard plus UNSAFE safeMode`() {
        val text = "include::chapter.adoc[]\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.STRICT, XrefValidationMode.OFF, SafeMode.UNSAFE,
        )
        assertEquals("REJECT", report.security.advice)
    }

    @Test
    fun `security advice VALID when SERVER safeMode`() {
        val text = "include::chapter.adoc[]\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.STRICT, XrefValidationMode.OFF, SafeMode.SERVER,
        )
        assertEquals("VALID", report.security.advice)
    }

    @Test
    fun `report serialises to non-empty JSON`() {
        val text = "[[a]]\n== A\n\n<<a>>\n"
        val report = DocumentValidator.validate(
            text, baseDir, IncludeGuardMode.STRICT, XrefValidationMode.STRICT, SafeMode.SERVER,
        )
        val json = report.toJson()
        assertTrue(json.contains("includeGuard"), "report must contain includeGuard block")
        assertTrue(json.contains("xref"), "report must contain xref block")
        assertTrue(json.contains("security"), "report must contain security block")
    }
}
