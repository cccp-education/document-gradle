@file:Suppress("unused")

package document.scenarios

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

class DocumentSteps(private val world: DocumentWorld) {

    @Given("a new document project")
    fun createNewDocumentProject() {
        world.createGradleProject()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with fake LLM")
    fun createNewDocumentProjectWithFakeLlm() {
        world.createGradleProjectWithFakeLlm()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with fake LLM and existing output")
    fun createNewDocumentProjectWithFakeLlmAndExistingOutput() {
        world.createGradleProjectWithFakeLlm(existingOutput = true)
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source")
    fun createNewDocumentProjectWithAsciiDocSource() {
        world.createGradleProjectWithAsciiDocSource()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source and an existing HTML output")
    fun createNewDocumentProjectWithAsciiDocSourceAndExistingHtmlOutput() {
        world.createGradleProjectWithAsciiDocSource(existingHtmlOutput = true)
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source and an existing PDF output")
    fun createNewDocumentProjectWithAsciiDocSourceAndExistingPdfOutput() {
        world.createGradleProjectWithAsciiDocSourceAndExistingPdf()
        assertThat(world.projectDir).exists()
    }

    @When("I am executing the task {string}")
    fun executeTask(taskName: String) {
        world.executeGradle(taskName)
    }

    @When("I am executing the task {string} with group {string}")
    fun executeTaskWithGroup(taskName: String, group: String) {
        world.executeGradle(taskName, "--group", group)
    }

    @When("I am executing the task {string} with prompt {string}")
    fun executeTaskWithPrompt(taskName: String, prompt: String) {
        world.executeGradle(taskName, "-Pdocument.prompt=$prompt")
    }

    @Then("the build should succeed")
    fun buildShouldSucceed() {
        assertThat(world.buildResult).isNotNull
    }

    @Then("the output should contain {string}")
    fun outputShouldContain(text: String) {
        assertThat(world.buildResult?.output).contains(text)
    }

    @Then("the generated document should be a valid AsciiDoc")
    fun generatedDocumentShouldBeValidAsciiDoc() {
        val doc = world.generatedDocument()
        assertThat(doc).exists()
        val content = doc!!.readText()
        assertThat(content).contains("= ")
    }

    @Then("the converted HTML file should exist")
    fun convertedHtmlFileShouldExist() {
        val html = world.convertedHtmlFile()
        assertThat(html).exists()
    }

    @Then("the converted HTML should contain a doctype declaration")
    fun convertedHtmlShouldContainDoctype() {
        val html = world.convertedHtmlFile()
        assertThat(html).exists()
        val content = html!!.readText()
        assertThat(content).containsIgnoringCase("<!DOCTYPE html")
    }

    @Then("the converted HTML should contain the source title")
    fun convertedHtmlShouldContainSourceTitle() {
        val html = world.convertedHtmlFile()
        assertThat(html).exists()
        val content = html!!.readText()
        assertThat(content).contains("Document de Test")
    }

    @Then("the converted PDF file should exist")
    fun convertedPdfFileShouldExist() {
        val pdf = world.convertedPdfFile()
        assertThat(pdf).exists()
    }

    @Then("the converted PDF should be a valid PDF document")
    fun convertedPdfShouldBeValidPdfDocument() {
        val pdf = world.convertedPdfFile()
        assertThat(pdf).exists()
        val bytes = pdf!!.readBytes()
        assertThat(bytes.size).isGreaterThan(100)
        val header = String(bytes.copyOfRange(0, minOf(5, bytes.size)))
        assertThat(header).startsWith("%PDF")
    }

    @After
    fun cleanup() {
        world.cleanup()
    }
}