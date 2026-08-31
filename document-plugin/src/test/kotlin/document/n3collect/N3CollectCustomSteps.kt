package document.n3collect

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@n3-collect-custom` scenarios (S-236).
 *
 * BDD with a real Gradle build (TestKit): the steps verify that the N3 collect
 * indexes artifacts under their real name when the `outputFileName` knob renames
 * the conversion output. Every step text carries the `n3-collect` prefix
 * (anti-glue pattern from S-223).
 */
class N3CollectCustomSteps {

    private lateinit var projectDir: File

    @Given("an n3-collect document project")
    fun `an n3-collect document project`() {
        projectDir = Files.createTempDirectory("n3-collect-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"n3-collect\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(
            """
            = Title

            == Intro

            [[intro]]
            Collect custom output content.
            """.trimIndent(),
        )
    }

    @When("the HTML conversion runs with a custom output file name")
    fun `the html conversion runs with a custom output file name`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "-Pdocument.outputFileName=custom")
            .withPluginClasspath()
            .build()
    }

    @When("the collectDocumentRetrieve task runs with the same custom output file name")
    fun `the collect task runs with the same custom output file name`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve", "-Pdocument.outputFileName=custom")
            .withPluginClasspath()
            .build()
    }

    @When("the HTML conversion runs with the default output file name")
    fun `the html conversion runs with the default output file name`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()
    }

    @When("the collectDocumentRetrieve task runs with default configuration")
    fun `the collect task runs with default configuration`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()
    }

    private val composite: String
        get() = projectDir.resolve("build/docs/document/composite-context.json").readText()

    @Then("n3-collect composite-context.json references custom.html")
    fun `composite references custom html`() {
        assertTrue(
            composite.contains("custom.html"),
            "composite-context.json must reference the custom-named artifact — actual: $composite",
        )
    }

    @Then("n3-collect composite-context.json does not reference document.html")
    fun `composite does not reference document html`() {
        assertFalse(
            composite.contains("\"document.html\""),
            "the canonical document.html must NOT be indexed when the knob renames the output",
        )
    }

    @Then("n3-collect composite-context.json references document.html")
    fun `composite references document html`() {
        assertTrue(
            composite.contains("document.html"),
            "composite-context.json must keep indexing the canonical artifact — actual: $composite",
        )
    }
}