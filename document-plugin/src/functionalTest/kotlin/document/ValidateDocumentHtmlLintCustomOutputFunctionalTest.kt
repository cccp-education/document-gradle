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
 * auditing the *real* rendered HTML when the `outputFileName` knob renames the
 * conversion output (S-235, Option B) : `validateDocument` (htmlFilePath) and
 * `lintHtmlDocument` derive the audited path from the same knob — never the
 * static `document.html` convention (S-232 limitation, now resolved).
 */
class ValidateDocumentHtmlLintCustomOutputFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    /** A source whose rendered HTML has a *manual* dead link (raw passthrough). The
     *  fragment is chosen so the discriminating finding (real custom HTML audited)
     *  cannot be confused with the static-path failure finding `<html-file-missing>`. */
    private val deadLinkSource = "= Title\n\n++++\n<p><a href=\"#gone-custom\">gone</a></p>\n++++\n"

    private fun writeBuild() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-validate-custom-html\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.validation.HtmlLinkLintMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                converter {
                    htmlLinkLint = HtmlLinkLintMode.STRICT
                    safeMode = SafeMode.SERVER
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(deadLinkSource)
    }

    private fun runAndFail(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .buildAndFail()

    private val reportPath get() = projectDir.resolve("build/docs/document/document-validation-report.json")

    @Test
    fun `fourth guard audits the real custom-named html and finds the dead link`() {
        writeBuild()
        val result = runAndFail("convertDocumentToHtml", "validateDocument", "-Pdocument.outputFileName=custom")
        val json = projectDir.resolve("build/docs/document/document-validation-report.json").readText()
        assertTrue(json.contains("htmlLint"), "the report must carry the fourth guard block (json: $json)")
        assertTrue(json.contains("DEAD"), "the lint must be DEAD on the custom-named HTML (json: $json)")
        assertTrue(json.contains("gone-custom"), "the dead fragment must be listed from the REAL custom HTML (json: $json)")
        assertFalse(json.contains("html-file-missing"), "the guard must NOT report the static path missing — it must audit the real custom.html (json: $json)")
        assertTrue(
            projectDir.resolve("build/docs/document/custom.html").exists(),
            "the custom.html file must exist (audited input)",
        )
        assertTrue(
            result.output.contains("HTML link lint"),
            "STRICT lint must fail the build on the real custom HTML (output: ${result.output})",
        )
    }

    @Test
    fun `lintHtmlDocument also audits the custom-named html`() {
        writeBuild()
        val result = runAndFail("lintHtmlDocument", "-Pdocument.outputFileName=custom")
        assertTrue(
            result.output.contains("dead internal link") || result.output.contains("HTML link lint"),
            "the standalone lint task must fail on the custom-named HTML (output: ${result.output})",
        )
        assertTrue(
            projectDir.resolve("build/docs/document/custom.html").exists(),
            "the custom.html file must exist (lint consumed it)",
        )
    }
}