package document.n3pipeline

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@n3-pipeline` scenarios (S-233).
 *
 * BDD with a real Gradle build (TestKit): the steps stage the N3 chain the way
 * runner-gradle N3 consumes it — build 1 produces the book (bookPipeline), build 2
 * audits it (validateDocument writes the composite report), build 3 re-indexes
 * (collectDocumentRetrieve, snapshot contract) so metadata.json carries the fresh
 * validationStatus. Every step text carries the `n3-pipeline` prefix (anti-glue
 * pattern from S-223).
 */
class N3PipelineSteps {

    private lateinit var projectDir: File
    private lateinit var metadataFile: File

    @Given("a book pipeline project with STRICT xref and HTML lint validation")
    fun `a book pipeline project with strict xref and html lint validation`() {
        projectDir = Files.createTempDirectory("n3-pipeline-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"n3-pipeline\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.validation.HtmlLinkLintMode
            import document.xref.XrefValidationMode

            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("N3 Pipeline Book")
                    author.set("N3 Author")
                }
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
                converter {
                    xrefValidation = XrefValidationMode.STRICT
                    htmlLinkLint = HtmlLinkLintMode.STRICT
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("pages").apply { mkdirs() }
            .resolve("001-chapter.adoc")
            .writeText("== Chapter One\n\nN3 pipeline content with a [[section-anchor]] anchor.\n")
    }

    @When("the bookPipeline task runs")
    fun `the book pipeline task runs`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("bookPipeline")
            .withPluginClasspath()
            .build()
    }

    @When("the validateDocument task runs in a second build")
    fun `the validate document task runs in a second build`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument")
            .withPluginClasspath()
            .build()
    }

    @When("the collectDocumentRetrieve task runs in a third build")
    fun `the collect document retrieve task runs in a third build`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()
    }

    @Then("n3-pipeline metadata.json carries validationStatus PASS")
    fun `the n3 pipeline metadata carries validation status pass`() {
        val metadata = projectDir.resolve("build/docs/document/metadata.json")
        assertTrue(metadata.exists(), "metadata.json must be produced by the collect")
        val content = metadata.readText()
        assertTrue(
            content.contains("\"validationStatus\" : \"PASS\""),
            "metadata.json must carry the fresh validationStatus PASS — actual: $content",
        )
    }
}