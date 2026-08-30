package document.includeguard

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@converter-include-guard` scenarios (DOC-CR4).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `converter { includeGuard = ... }` block
 * and a `printIncludeGuard` task, run it, and assert the resulting guard of
 * `convertDocumentToHtml`. All step texts carry the `` prefix (anti-glue
 * pattern), and the CLI scenario passes an extra `-Pdocument.includeGuard`
 * argument.
 */
class IncludeGuardSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String

    @Given("a document gradle project with converter includeGuard {string}")
    fun `a project with converter includeGuard`(mode: String) {
        projectDir = Files.createTempDirectory("doc-include-guard-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-include-guard\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask
            import document.security.IncludeGuardMode

            plugins {
                id("education.cccp.document")
            }

            document {
                converter { includeGuard = IncludeGuardMode.$mode }
            }

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printIncludeGuard") {
                doLast { println("INCLUDEGUARD=" + htmlTask.get().includeGuard.get()) }
            }
            """.trimIndent(),
        )
    }

    @Given("a document gradle project with no include guard configuration")
    fun `a project with no include guard configuration`() {
        projectDir = Files.createTempDirectory("doc-include-guard-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-include-guard\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask

            plugins {
                id("education.cccp.document")
            }

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printIncludeGuard") {
                doLast { println("INCLUDEGUARD=" + htmlTask.get().includeGuard.get()) }
            }
            """.trimIndent(),
        )
    }

    @When("the plugin is applied and printIncludeGuard runs")
    fun `the plugin is applied and printIncludeGuard runs`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("printIncludeGuard", "-q")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the plugin is applied and printIncludeGuard runs with CLI {string}")
    fun `the plugin is applied and printIncludeGuard runs with CLI`(cliMode: String) {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("printIncludeGuard", "-q", "-Pdocument.includeGuard=$cliMode")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @Then("the convertDocumentToHtml task includeGuard is {string}")
    fun `the task includeGuard is`(expected: String) {
        assertTrue(
            buildOutput.contains("INCLUDEGUARD=$expected"),
            "le includeGuard de la tâche doit être $expected (sortie: $buildOutput)",
        )
    }
}
