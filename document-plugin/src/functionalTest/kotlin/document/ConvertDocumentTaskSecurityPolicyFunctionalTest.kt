package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-CR5 conversion security
 * policy : coherence of AsciidoctorJ `SafeMode` × `includeGuard`.
 */
class ConvertDocumentTaskSecurityPolicyFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(
        documentBlock: String,
        withSource: Boolean = true,
        sourceContent: String = "= Title\n\nValid content.\n",
    ) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-security-policy\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask
            import document.security.IncludeGuardMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            $documentBlock
            """.trimIndent(),
        )
        if (withSource) {
            projectDir.resolve("doc.adoc").writeText(sourceContent)
        }
    }

    private fun documentBlock(
        safeMode: String,
        includeGuard: String,
    ) = "document {\n" +
        "    source.set(file(\"doc.adoc\"))\n" +
        "    converter { safeMode = SafeMode.$safeMode\n" +
        "    includeGuard = IncludeGuardMode.$includeGuard }\n" +
        "}"

    private fun run(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .withPluginClasspath()
            .forwardOutput()
            .build()

    private fun runAndFail(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

    @Test
    fun `STRICT include guard with UNSAFE safeMode fails the build fast`() {
        writeBuild(
            documentBlock = documentBlock("UNSAFE", "STRICT"),
        )

        val result = runAndFail("convertDocumentToHtml")

        assertTrue(
            result.output.contains("Security policy (STRICT)"),
            "la configuration asymétrique STRICT+UNSAFE doit être rejetée (sortie: ${result.output})",
        )
    }

    @Test
    fun `LENIENT include guard with UNSAFE safeMode warns but still converts`() {
        writeBuild(
            documentBlock = documentBlock("UNSAFE", "LENIENT"),
        )

        val result = run("convertDocumentToHtml")

        val html = projectDir.resolve("build/docs/document/document.html")
        assertTrue(html.exists(), "la conversion LENIENT doit produire le livrable HTML")
        assertTrue(
            result.output.contains("Security policy (LENIENT)"),
            "l'asymétrie LENIENT+UNSAFE doit être signalée par warn (sortie: ${result.output})",
        )
    }

    @Test
    fun `SERVER safeMode with STRICT include guard is coherent and converts`() {
        writeBuild(
            documentBlock = documentBlock("SERVER", "STRICT"),
        )

        val result = run("convertDocumentToHtml")

        val html = projectDir.resolve("build/docs/document/document.html")
        assertTrue(html.exists(), "la conversion SERVER+STRICT doit produire le livrable HTML")
        assertTrue(
            !result.output.contains("Security policy"),
            "une config cohérente ne doit pas déclencher la politique (sortie: ${result.output})",
        )
    }
}
