package document.ocrquality

import document.BookOcrFailureDetector
import document.BookOcrIssue
import document.BookOcrIssueReport
import document.BookSection
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step definitions for the `@ocr-quality` scenarios (OCR-QUALITY-3).
 *
 * Pure BDD: the steps exercise [document.BookOcrFailureDetector] and
 * [document.BookOcrIssueReport] directly on a temporary scans directory — no
 * Gradle task, no external I/O beyond temp files. All step texts carry the
 * `ocr-quality ` prefix (anti-glue-collision pattern, S-223).
 */
class OcrQualitySteps {

    private lateinit var scansDir: File
    private var issues: List<BookOcrIssue> = emptyList()
    private var reportFile: File? = null

    @Before
    fun setup() {
        scansDir = File.createTempFile("ocr-quality-", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        issues = emptyList()
        reportFile = null
    }

    @Given("ocr-quality a scans directory with page {string} containing")
    fun `a scans directory with page containing`(fileName: String, body: String) {
        // Scenarios may add several pages against the same directory.
        File(scansDir, fileName).writeText(body)
    }

    @Given("ocr-quality a scans directory with page {string} containing {string}")
    fun `a scans directory with page containing inline`(fileName: String, body: String) {
        File(scansDir, fileName).writeText(body)
    }

    @Given("ocr-quality the file {string} exists in the scans directory")
    fun `the file exists in the scans directory`(fileName: String) {
        File(scansDir, fileName).writeBytes(byteArrayOf(1, 2, 3))
    }

    @When("ocr-quality the OCR failures are detected")
    fun `the OCR failures are detected`() {
        issues = BookOcrFailureDetector.detect(scansDir, emptyList())
    }

    @When("ocr-quality the OCR failures are detected and written to a report")
    fun `the OCR failures are detected and written to a report`() {
        issues = BookOcrFailureDetector.detect(scansDir, emptyList())
        reportFile = File(scansDir.parentFile, "book-ocr-issues.json")
        BookOcrIssueReport.write(issues, reportFile!!)
    }

    @Then("ocr-quality no issue is reported")
    fun `no issue is reported`() {
        assertEquals(emptyList(), issues)
    }

    @Then("ocr-quality the issues are")
    fun `the issues are`(expected: List<Map<String, String>>) {
        val actual = issues.map { issue -> mapOf("reason" to issue.reason.name, "detail" to (issue.detail ?: "")) }
        val expect = expected.map { row ->
            // Cucumber escapes literal pipes in table cells with a backslash —
            // strip the escapes so the row matches the raw line content.
            mapOf("reason" to (row["reason"] ?: ""), "detail" to (row["detail"] ?: "").replace("\\|", "|"))
        }
        assertEquals(expect, actual, "detected issues differ from expected")
    }

    @Then("ocr-quality the ILLISIBLE issue carries no detail field")
    fun `the ILLISIBLE issue carries no detail field`() {
        val report = reportFile?.readText() ?: error("report not written")
        val line = report.lineSequence().single { "\"reason\": \"ILLISIBLE\"" in it }
        assertFalse("detail" in line, "ILLISIBLE must not carry a detail field: $line")
    }

    @Then("ocr-quality the IMAGE_MISSING issue carries detail {string}")
    fun `the IMAGE_MISSING issue carries detail`(expected: String) {
        val report = reportFile?.readText() ?: error("report not written")
        val line = report.lineSequence().single { "\"reason\": \"IMAGE_MISSING\"" in it }
        assertTrue(expected in line, "IMAGE_MISSING must carry detail '$expected': $line")
    }
}