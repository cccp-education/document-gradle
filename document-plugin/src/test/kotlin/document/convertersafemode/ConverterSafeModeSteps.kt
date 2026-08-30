package document.convertersafemode

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@converter-safe-mode` scenarios (DOC-CR3-2).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script
 * that applies the document plugin with a `converter { safeMode = ... }`
 * block and a `printSafeMode` task, run it, and assert the resulting
 * SafeMode of `convertDocumentToHtml`. All step texts carry the
 * `` prefix (anti-glue-collision pattern).
 */
class ConverterSafeModeSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String

    @Given("a document gradle project with converter safeMode {string}")
    fun `a project with converter safeMode`(mode: String) {
        projectDir = Files.createTempDirectory("doc-converter-safe-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-converter-safe\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import org.asciidoctor.SafeMode
            import document.ConvertDocumentTask

            plugins {
                id("education.cccp.document")
            }

            document {
                converter { safeMode = SafeMode.$mode }
            }

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printSafeMode") {
                doLast { println("SAFEMODE=" + htmlTask.get().safeMode.get()) }
            }
            """.trimIndent(),
        )
    }

    @Given("a document gradle project with no converter configuration")
    fun `a project with no converter configuration`() {
        projectDir = Files.createTempDirectory("doc-converter-safe-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-converter-safe\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask

            plugins {
                id("education.cccp.document")
            }

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printSafeMode") {
                doLast { println("SAFEMODE=" + htmlTask.get().safeMode.get()) }
            }
            """.trimIndent(),
        )
    }

    @When("the plugin is applied and printSafeMode runs")
    fun `the plugin is applied and printSafeMode runs`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("printSafeMode", "-q")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @Then("the convertDocumentToHtml task safeMode is {string}")
    fun `the task safeMode is`(expected: String) {
        assertTrue(
            buildOutput.contains("SAFEMODE=$expected"),
            "le safeMode de la tâche doit être $expected (sortie: $buildOutput)",
        )
    }
}
