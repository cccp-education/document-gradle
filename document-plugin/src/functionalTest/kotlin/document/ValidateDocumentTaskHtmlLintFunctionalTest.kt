package document

import org.asciidoctor.SafeMode
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the fourth composite guard
 * (DOC-VALIDATE-HTML-LINT) wiring in [ValidateDocumentTask] : the HTML link lint of the
 * *rendered* HTML joins include guard + xref + security in the consolidated report
 * (`document-validation-report.json`), severity STRICT fail-fast / LENIENT warn / OFF skip.
 */
class ValidateDocumentTaskHtmlLintFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(converterBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-validate-html-lint\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.security.IncludeGuardMode
            import document.validation.HtmlLinkLintMode
            import document.xref.XrefValidationMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $converterBlock
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(sourceContent)
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .build()

    private fun runAndFail(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .buildAndFail()

    private val reportPath get() = projectDir.resolve("build/docs/document/document-validation-report.json")

    /** A source whose rendered HTML has no dead internal link (explicit [[anchor]]). */
    private val navigableSource = "= Title\n\n[[intro]]\n== Intro\n\nSee <<intro>> here.\n"

    /** A source whose rendered HTML has a *manual* dead link (raw passthrough). */
    private val deadLinkSource = "= Title\n\n++++\n<p><a href=\"#missing\">gone</a></p>\n++++\n"

    @Test
    fun `OFF skips the HTML lint guard (no htmlLint block in report)`() {
        writeBuild(converterBlock = "", sourceContent = deadLinkSource)
        run("convertDocumentToHtml", "validateDocument")
        val json = reportPath.readText()
        assertFalse(json.contains("htmlLint"), "en OFF le lint HTML n'est pas audité")
    }

    @Test
    fun `STRICT lint on HTML with dead link fails fast and report marks DEAD`() {
        writeBuild(converterBlock = "converter { htmlLinkLint = HtmlLinkLintMode.STRICT }", sourceContent = deadLinkSource)
        val result = runAndFail("convertDocumentToHtml", "validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("htmlLint"), "le rapport doit contenir le bloc 4e garde")
        assertTrue(json.contains("DEAD"), "le rapport doit marquer le lint DEAD")
        assertTrue(json.contains("missing"), "le rapport doit lister le fragment mort")
        assertTrue(result.output.contains("HTML link lint"), "la tâche doit échouer sur lint STRICT (sortie: ${result.output})")
    }

    @Test
    fun `STRICT lint on navigable HTML passes and report marks VALID`() {
        writeBuild(converterBlock = "converter { htmlLinkLint = HtmlLinkLintMode.STRICT }", sourceContent = navigableSource)
        run("convertDocumentToHtml", "validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("\"status\" : \"VALID\""), "le lint doit être VALID sur un HTML navigable")
        assertFalse(json.contains("DEAD"), "aucun lien mort attendu")
    }

    @Test
    fun `LENIENT lint warns and does not fail the build`() {
        writeBuild(converterBlock = "converter { htmlLinkLint = HtmlLinkLintMode.LENIENT }", sourceContent = deadLinkSource)
        run("convertDocumentToHtml", "validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("DEAD"), "rapport LENIENT : lint DEAD visible")
    }
}