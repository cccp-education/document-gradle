package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-CR4 include-path
 * guard : DSL/CLI wiring (mirrors DOC-CR3-2 safe-mode) + the pre-flight audit
 * actually failing/warning a conversion when a forbidden `include::` is found.
 */
class ConvertDocumentTaskIncludeGuardFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(
        converterBlock: String,
        withSource: Boolean = false,
        sourceContent: String = "",
    ) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-include-guard\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask
            import document.security.IncludeGuardMode

            plugins {
                id("education.cccp.document")
            }

            $converterBlock

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printIncludeGuard") {
                doLast { println("INCLUDEGUARD=" + htmlTask.get().includeGuard.get()) }
            }
            """.trimIndent(),
        )
        if (withSource) {
            projectDir.resolve("doc.adoc").writeText(sourceContent)
        }
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

    @Test
    fun `converter DSL includeGuard STRICT is propagated to convertDocumentToHtml`() {
        writeBuild(converterBlock = "document {\n    converter { includeGuard = IncludeGuardMode.STRICT }\n}")

        val result = run("printIncludeGuard", "-q")

        assertTrue(
            result.output.contains("INCLUDEGUARD=STRICT"),
            "le includeGuard du block converter doit être propagé (sortie: ${result.output})",
        )
    }

    @Test
    fun `cli includeGuard LENIENT overrides the DSL STRICT`() {
        writeBuild(converterBlock = "document {\n    converter { includeGuard = IncludeGuardMode.STRICT }\n}")

        val result = run("printIncludeGuard", "-q", "-Pdocument.includeGuard=LENIENT")

        assertTrue(
            result.output.contains("INCLUDEGUARD=LENIENT"),
            "le flag CLI doit primer sur le DSL (sortie: ${result.output})",
        )
    }

    @Test
    fun `default includeGuard is OFF when nothing is configured`() {
        writeBuild(converterBlock = "")

        val result = run("printIncludeGuard", "-q")

        assertTrue(
            result.output.contains("INCLUDEGUARD=OFF"),
            "le défaut doit rester OFF (backward-compat) (sortie: ${result.output})",
        )
    }

    @Test
    fun `STRICT guard fails the build on a path traversal include`() {
        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n    converter { includeGuard = IncludeGuardMode.STRICT }\n}",
            withSource = true,
            sourceContent = "= Traversal\ninclude::../secret.adoc[]\n",
        )

        val result = runAndFail("convertDocumentToHtml")

        assertTrue(
            result.output.contains("Include guard (STRICT)"),
            "la conversion doit échouer sur un include traversant (sortie: ${result.output})",
        )
    }

    @Test
    fun `LENIENT guard warns but converts when the escaped file exists`() {
        // Create the escaped target so AsciidoctorJ (UNSAFE) can actually read it.
        projectDir.parentFile.resolve("secret.adoc").writeText("= Secret\n")

        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n    converter { includeGuard = IncludeGuardMode.LENIENT }\n}",
            withSource = true,
            sourceContent = "= Traversal\ninclude::../secret.adoc[]\n",
        )

        val result = run("convertDocumentToHtml")

        val html = projectDir.resolve("build/docs/document/document.html")
        assertTrue(html.exists(), "la conversion LENIENT doit tout de même produire le livrable HTML")
        assertTrue(
            html.readText().contains("Secret"),
            "le include traversant doit avoir été intégré en mode LENIENT (sortie: ${result.output})",
        )
    }
}
