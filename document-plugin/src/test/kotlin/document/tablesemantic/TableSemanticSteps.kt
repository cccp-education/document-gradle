package document.tablesemantic

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@table-semantic` scenarios (EPIC DOC-SEMANTIC-TABLE,
 * US-5).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `semantic { schema("planning") { … } }`
 * block and the given source, run `collectTableSemantics`, and assert the
 * outcome (extract / ignore / finding / reject / skip). All step texts carry the
 * `table-semantic` prefix (anti-glue pattern from S-088/S-223).
 */
class TableSemanticSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String
    private lateinit var reportFile: File

    @Given("a document gradle project with tableSemantics {string} and source {string}")
    fun `a project with tableSemantics and source`(mode: String, source: String) {
        projectDir = Files.createTempDirectory("doc-table-semantic-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-table-semantic\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.semantic.TableSemanticsMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                semantic {
                    tableSemantics.set(TableSemanticsMode.$mode)
                    schema("planning") {
                        column(0, role = "reference")
                        column("Title", role = "title")
                        column(2, role = "objective", required = true)
                    }
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(source)
        reportFile = projectDir.resolve("build/docs/document/table-semantics.json")
    }

    @When("the collectTableSemantics task runs successfully")
    fun `collectTableSemantics runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectTableSemantics")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the collectTableSemantics task runs and fails")
    fun `collectTableSemantics runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectTableSemantics")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @Then("the table-semantics report contains table {string} with role {string}")
    fun `report contains table with role`(table: String, role: String) {
        assertTrue(reportFile.exists(), "the report must be written in LENIENT")
        val json = reportFile.readText()
        assertTrue(json.contains("\"$table\""), "the report must name the table '$table'")
        assertTrue(json.contains("\"$role\""), "the report must carry the projected role '$role'")
    }

    @Then("the table-semantics report contains no extracted table")
    fun `report contains no extracted table`() {
        assertTrue(reportFile.exists(), "the report must still be written")
        assertTrue(
            !reportFile.readText().contains("\"records\""),
            "an un-annotated table must never be lifted (no records exported)",
        )
    }

    @Then("the table-semantics report records a required-role finding {string}")
    fun `report records a required-role finding`(role: String) {
        assertTrue(reportFile.exists(), "the report must be written (even in STRICT)")
        val json = reportFile.readText()
        assertTrue(json.contains("is blank") && json.contains(role), "the report must record the finding on '$role'")
    }

    @Then("the build fails with table semantics message {string}")
    fun `build fails with table semantics message`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "STRICT must reject the lifting (output: $buildOutput)",
        )
    }

    @Then("no table-semantics.json is written")
    fun `no report written`() {
        assertTrue(!reportFile.exists(), "OFF must not produce a report")
    }
}
