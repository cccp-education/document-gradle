package document.securitypolicy

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@converter-security-policy` scenarios (DOC-CR5).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `converter { safeMode = ... ; includeGuard = ... }`
 * block and a valid source, run `convertDocumentToHtml`, and assert the security
 * policy outcome (reject / warn / coherent). All step texts carry the `securitypolicy`
 * prefix (anti-glue pattern).
 */
class SecurityPolicySteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String

    @Given("a document gradle project with safeMode {string} and includeGuard {string}")
    fun `a project with safeMode and includeGuard`(safeMode: String, includeGuard: String) {
        projectDir = Files.createTempDirectory("doc-security-policy-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-security-policy\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask
            import document.security.IncludeGuardMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                converter {
                    safeMode = SafeMode.$safeMode
                    includeGuard = IncludeGuardMode.$includeGuard
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText("= Title\n\nValid content.\n")
    }

    @When("the plugin is applied and convertDocumentToHtml runs and fails")
    fun `convertDocumentToHtml runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @When("the plugin is applied and convertDocumentToHtml runs successfully")
    fun `convertDocumentToHtml runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @Then("the build fails with security policy message {string}")
    fun `build fails with security policy message`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "la configuration asymétrique doit être rejetée par la politique (sortie: $buildOutput)",
        )
    }

    @Then("the build succeeds with security policy warning {string}")
    fun `build succeeds with security policy warning`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "l'asymétrie doit être signalée par warn (sortie: $buildOutput)",
        )
    }

    @Then("the build succeeds without security policy message")
    fun `build succeeds without security policy message`() {
        assertTrue(
            !buildOutput.contains("Security policy"),
            "une config cohérente ne doit pas déclencher la politique (sortie: $buildOutput)",
        )
    }
}
