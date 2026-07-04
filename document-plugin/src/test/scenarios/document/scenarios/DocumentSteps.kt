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

    @Given("a new publishable document project")
    fun createNewPublishableDocumentProject() {
        world.createPublishableGradleProject()
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
    fun createNewDocumentProjectWithAsciiDocSourceAndExistingPdf() {
        world.createGradleProjectWithAsciiDocSourceAndExistingPdf()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source and an existing EPUB output")
    fun createNewDocumentProjectWithAsciiDocSourceAndExistingEpub() {
        world.createGradleProjectWithAsciiDocSourceAndExistingEpub()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc manpage source")
    fun createNewDocumentProjectWithAsciiDocManpageSource() {
        world.createGradleProjectWithAsciiDocManpageSource()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source containing a plantuml block")
    fun createNewDocumentProjectWithAsciiDocSourceContainingPlantuml() {
        world.createGradleProjectWithAsciiDocSourceContainingPlantuml()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source containing a passthrough block")
    fun createNewDocumentProjectWithAsciiDocSourceContainingPassthrough() {
        world.createGradleProjectWithAsciiDocSourceContainingPassthrough()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source containing an include directive")
    fun createNewDocumentProjectWithAsciiDocSourceContainingInclude() {
        world.createGradleProjectWithAsciiDocSourceContainingInclude()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source and a custom theme")
    fun createNewDocumentProjectWithAsciiDocSourceAndCustomTheme() {
        world.createGradleProjectWithAsciiDocSourceAndCustomTheme()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source and a converted HTML artifact")
    fun createNewDocumentProjectWithAsciiDocSourceAndConvertedHtml() {
        world.createGradleProjectWithAsciiDocSourceAndConvertedHtml()
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

    @Then("the converted HTML should contain the custom stylesheet link")
    fun convertedHtmlShouldContainCustomStylesheetLink() {
        val html = world.convertedHtmlFile()
        assertThat(html).exists()
        val content = html!!.readText()
        assertThat(content).containsIgnoringCase("font-family")
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

    @Then("the converted EPUB file should exist")
    fun convertedEpubFileShouldExist() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
    }

    @Then("the converted EPUB should be a valid EPUB document")
    fun convertedEpubShouldBeValidEpubDocument() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val bytes = epub!!.readBytes()
        assertThat(bytes.size).isGreaterThan(100)
        // EPUB est un zip — signature PK\x03\x04
        val header = String(bytes.copyOfRange(0, minOf(4, bytes.size)))
        assertThat(header).startsWith("PK")
    }

    @Then("the converted DocBook file should exist")
    fun convertedDocBookFileShouldExist() {
        val docbook = world.convertedDocBookFile()
        assertThat(docbook).exists()
    }

    @Then("the converted DocBook should be a valid DocBook document")
    fun convertedDocBookShouldBeValidDocBookDocument() {
        val docbook = world.convertedDocBookFile()
        assertThat(docbook).exists()
        val content = docbook!!.readText()
        // DocBook 5 — namespace XML + racine <book> ou <article>
        assertThat(content).containsIgnoringCase("xmlns")
        assertThat(content).containsAnyOf("<book", "<article")
    }

    @Then("the converted ManPage file should exist")
    fun convertedManPageFileShouldExist() {
        val manpage = world.convertedManPageFile()
        assertThat(manpage).exists()
    }

    @Then("the converted ManPage should be a valid manpage document")
    fun convertedManPageShouldBeValidManpageDocument() {
        val manpage = world.convertedManPageFile()
        assertThat(manpage).exists()
        val content = manpage!!.readText()
        // Format troff — commence par .TH ou .ds
        assertThat(content).containsAnyOf(".TH", ".ds", ".SH")
    }

    @Then("the enriched document should exist")
    fun enrichedDocumentShouldExist() {
        val enriched = world.enrichedDocumentFile()
        assertThat(enriched).exists()
    }

    @Then("the enriched document should preserve the plantuml block")
    fun enrichedDocumentShouldPreservePlantumlBlock() {
        val enriched = world.enrichedDocumentFile()
        assertThat(enriched).exists()
        val content = enriched!!.readText()
        assertThat(content).contains("[plantuml]")
        assertThat(content).contains("@startuml")
        assertThat(content).contains("@enduml")
    }

    @Then("the enriched document should preserve the passthrough block")
    fun enrichedDocumentShouldPreservePassthroughBlock() {
        val enriched = world.enrichedDocumentFile()
        assertThat(enriched).exists()
        val content = enriched!!.readText()
        assertThat(content).contains("++++")
        assertThat(content).contains("<iframe")
    }

    @Then("the enriched document should contain the included content")
    fun enrichedDocumentShouldContainIncludedContent() {
        val enriched = world.enrichedDocumentFile()
        assertThat(enriched).exists()
        val content = enriched!!.readText()
        assertThat(content).contains("Chapitre Inclu")
    }

    @Then("the metadata json file should exist")
    fun metadataJsonFileShouldExist() {
        val metadata = world.metadataJsonFile()
        assertThat(metadata).exists()
    }

    @Then("the composite context json file should exist")
    fun compositeContextJsonFileShouldExist() {
        val composite = world.compositeContextJsonFile()
        assertThat(composite).exists()
    }

    @Then("the metadata json should contain source new-orleans")
    fun metadataJsonShouldContainSourceNewOrleans() {
        val metadata = world.metadataJsonFile()
        assertThat(metadata).exists()
        val content = metadata!!.readText()
        assertThat(content).contains("new-orleans")
    }

    @Then("the composite context json should contain the HTML artifact entry")
    fun compositeContextJsonShouldContainHtmlArtifactEntry() {
        val composite = world.compositeContextJsonFile()
        assertThat(composite).exists()
        val content = composite!!.readText()
        assertThat(content).contains(".html")
        assertThat(content).contains("entries")
    }

    @Then("the composite context json should contain count zero")
    fun compositeContextJsonShouldContainCountZero() {
        val composite = world.compositeContextJsonFile()
        assertThat(composite).exists()
        val content = composite!!.readText()
        assertThat(content).contains("\"count\" : 0")
    }

    @Then("the generated POM should contain the Document Gradle Plugin name")
    fun generatedPomShouldContainDocumentGradlePluginName() {
        val pom = world.generatedPomFile()
        assertThat(pom).exists()
        val content = pom!!.readText()
        assertThat(content).contains("Document Gradle Plugin")
    }

    @Then("the generated POM should contain the Apache-2.0 license")
    fun generatedPomShouldContainApacheLicense() {
        val pom = world.generatedPomFile()
        assertThat(pom).exists()
        val content = pom!!.readText()
        assertThat(content).contains("Apache-2.0")
    }

    @Then("the generated POM should contain the cccp-education developer")
    fun generatedPomShouldContainCccpEducationDeveloper() {
        val pom = world.generatedPomFile()
        assertThat(pom).exists()
        val content = pom!!.readText()
        assertThat(content).contains("cccp-education")
    }

    @Then("the generated POM should contain the scm connection")
    fun generatedPomShouldContainScmConnection() {
        val pom = world.generatedPomFile()
        assertThat(pom).exists()
        val content = pom!!.readText()
        assertThat(content).contains("scm:git")
    }

    @After
    fun cleanup() {
        world.cleanup()
    }
}