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

    @Given("a new document project with OCR pages directory")
    fun createNewDocumentProjectWithOcrPages() {
        world.createGradleProjectWithOcrPages()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with OCR pages and photos directory")
    fun createNewDocumentProjectWithOcrPagesAndPhotos() {
        world.createGradleProjectWithOcrPages(photos = true)
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with OCR pages directory and book formats")
    fun createNewDocumentProjectWithOcrPagesAndFormats() {
        world.createGradleProjectWithOcrPages(formats = true)
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with OCR pages and photos directory and a source pointing to the assembled book")
    fun createNewDocumentProjectWithOcrPagesPhotosAndBookSource() {
        world.createGradleProjectWithOcrPagesAndSourcePointingToBook()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a unified DSL enrich block")
    fun createNewDocumentProjectWithUnifiedEnrichBlock() {
        world.createGradleProjectWithUnifiedEnrichBlock()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a unified DSL outputs block")
    fun createNewDocumentProjectWithUnifiedOutputsBlock() {
        world.createGradleProjectWithUnifiedOutputsBlock()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a unified DSL metadata block")
    fun createNewDocumentProjectWithUnifiedMetadataBlock() {
        world.createGradleProjectWithUnifiedMetadataBlock()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a full unified DSL document block")
    fun createNewDocumentProjectWithFullUnifiedDslBlock() {
        world.createGradleProjectWithFullUnifiedDslBlock()
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

    @When("I am executing the task {string} twice in a row")
    fun executeTaskTwice(taskName: String) {
        world.executeGradle(taskName)
        val cfg = world.documentConfigJsonFile()
        world.lastConfigJson = cfg?.readText()
        cfg?.delete()
        world.executeGradle(taskName)
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

    @Then("the converted EPUB should embed the image directive as a zip entry")
    fun convertedEpubShouldEmbedImageDirectiveAsZipEntry() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val entries = java.util.zip.ZipFile(epub).use { zf ->
            zf.entries().toList().map { it.name }
        }
        assertThat(entries).anySatisfy { name ->
            assertThat(name).contains("photo.png")
        }
    }

    // --- US-DOC-04 (P2) : EPUB advanced rendering (tableaux, code, listes) ---

    @Given("a new document project with an AsciiDoc source containing ordered and unordered lists")
    fun createNewDocumentProjectWithOrderedAndUnorderedLists() {
        world.createGradleProjectWithAsciiDocSourceWithLists()
        assertThat(world.projectDir).exists()
    }

    @Then("the converted EPUB should render the table as a XHTML table element")
    fun convertedEpubShouldRenderTableAsXhtmlTableElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("<table")
        assertThat(xhtml).contains("Colonne A")
    }

    @Then("the converted EPUB should render the code block as a pre code element")
    fun convertedEpubShouldRenderCodeBlockAsPreCodeElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("<pre")
        assertThat(xhtml).containsIgnoringCase("<code")
        assertThat(xhtml).contains("fun main")
    }

    @Then("the converted EPUB should render the unordered list as a ul element")
    fun convertedEpubShouldRenderUnorderedListAsUlElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("<ul")
        assertThat(xhtml).contains("Element un")
    }

    @Then("the converted EPUB should render the ordered list as an ol element")
    fun convertedEpubShouldRenderOrderedListAsOlElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("<ol")
        assertThat(xhtml).contains("Premier")
    }

    // --- US-DOC-04 (P2) : EPUB advanced rendering (admonitions, sidebars, bibliographie) ---

    @Given("a new document project with an AsciiDoc source containing admonitions")
    fun createNewDocumentProjectWithAdmonitions() {
        world.createGradleProjectWithAsciiDocSourceWithAdmonitions()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source containing a sidebar block")
    fun createNewDocumentProjectWithSidebarBlock() {
        world.createGradleProjectWithAsciiDocSourceWithSidebar()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with an AsciiDoc source containing a bibliography block")
    fun createNewDocumentProjectWithBibliographyBlock() {
        world.createGradleProjectWithAsciiDocSourceWithBibliography()
        assertThat(world.projectDir).exists()
    }

    @Then("the converted EPUB should render the admonitions as aside elements")
    fun convertedEpubShouldRenderAdmonitionsAsAsideElements() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("admonition note")
        assertThat(xhtml).containsIgnoringCase("admonition tip")
        assertThat(xhtml).containsIgnoringCase("admonition warning")
        assertThat(xhtml).contains("Ceci est une note importante")
    }

    @Then("the converted EPUB should render the sidebar as an aside sidebar element")
    fun convertedEpubShouldRenderSidebarAsAsideSidebarElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("sidebar")
        assertThat(xhtml).contains("Contenu du sidebar")
    }

    @Then("the converted EPUB should render the bibliography as a bibliography element")
    fun convertedEpubShouldRenderBibliographyAsBibliographyElement() {
        val epub = world.convertedEpubFile()
        assertThat(epub).exists()
        val xhtml = world.extractEpubXhtml(epub!!)
        assertThat(xhtml).containsIgnoringCase("bibliography")
        assertThat(xhtml).contains("Author")
        assertThat(xhtml).contains("ref1")
    }

    // --- US-DOC-06/07 (P3) — DocBook + ManPage advanced rendering ---

    @Given("a new document project with an AsciiDoc source containing a table and a code block")
    fun createNewDocumentProjectWithTableAndCode() {
        world.createGradleProjectWithAsciiDocSourceWithTableAndCode()
        assertThat(world.projectDir).exists()
    }

    @Then("the converted DocBook should render the table as a DocBook table element")
    fun convertedDocBookShouldRenderTableAsDocBookTableElement() {
        val docbook = world.convertedDocBookFile()
        assertThat(docbook).exists()
        val content = docbook!!.readText()
        val hasTable = content.contains("<table", ignoreCase = true) || content.contains("<informaltable", ignoreCase = true)
        assertThat(hasTable).`as`("the DocBook must contain a <table> or <informaltable> element").isTrue()
    }

    @Then("the converted DocBook should render the code block as a programlisting element")
    fun convertedDocBookShouldRenderCodeBlockAsProgramlistingElement() {
        val docbook = world.convertedDocBookFile()
        assertThat(docbook).exists()
        val content = docbook!!.readText()
        assertThat(content).containsIgnoringCase("<programlisting")
        assertThat(content).contains("fun main")
    }

    @Given("a new document project with an AsciiDoc manpage source with formatted options")
    fun createNewDocumentProjectWithManpageWithOptions() {
        world.createGradleProjectWithAsciiDocManpageWithOptions()
        assertThat(world.projectDir).exists()
    }

    @Then("the converted ManPage should render the options in bold troff directives")
    fun convertedManPageShouldRenderOptionsInBoldTroffDirectives() {
        val manpage = world.convertedManPageFile()
        assertThat(manpage).exists()
        val content = manpage!!.readText()
        assertThat(content).contains("SYNOPSIS")
        val hasBold = content.contains(".B ") || content.contains("\\fB")
        assertThat(hasBold).`as`("the manpage must contain a bold troff directive (.B or \\fB)").isTrue()
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

    @Then("the composite context json should index the html pdf and epub book artifacts")
    fun compositeContextJsonShouldIndexHtmlPdfEpubBookArtifacts() {
        val composite = world.compositeContextJsonFile()
        assertThat(composite).exists()
        val content = composite!!.readText()
        assertThat(content).contains(".html")
        assertThat(content).contains(".pdf")
        assertThat(content).contains(".epub")
        assertThat(content).contains("\"entries\"")
        assertThat(content).contains("\"count\"")
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

    @Then("the assembled book file should exist")
    fun assembledBookFileShouldExist() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
    }

    @Then("the assembled book should contain the book title")
    fun assembledBookShouldContainBookTitle() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains("= Test Book")
    }

    @Then("the assembled book should contain all page contents in order")
    fun assembledBookShouldContainAllPageContentsInOrder() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        val firstIdx = content.indexOf("Chapter 1")
        val secondIdx = content.indexOf("Chapter 2")
        assertThat(firstIdx).isGreaterThan(0)
        assertThat(secondIdx).isGreaterThan(firstIdx)
    }

    @Then("the assembled book should contain photo image directives")
    fun assembledBookShouldContainPhotoImageDirectives() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains("image::001-page.png[]")
        assertThat(content).contains("image::002-page.png[]")
    }

    // --- DOC-12 unified DSL then steps ---

    @Then("the document config json file should exist")
    fun documentConfigJsonFileShouldExist() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
    }

    @Then("the document config json should contain the source filename")
    fun documentConfigJsonShouldContainSourceFilename() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("livre.adoc")
    }

    @Then("the document config json should contain the enrich flags")
    fun documentConfigJsonShouldContainEnrichFlags() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"plantuml\" : true")
        assertThat(content).contains("\"images\" : true")
        assertThat(content).contains("\"passthrough\" : true")
    }

    @Then("the document config json should contain the outputs flags")
    fun documentConfigJsonShouldContainOutputsFlags() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"html\" : true")
        assertThat(content).contains("\"pdf\" : true")
        assertThat(content).contains("\"epub\" : true")
    }

    @Then("the document config json should contain the metadata title")
    fun documentConfigJsonShouldContainMetadataTitle() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("Mon Livre")
        assertThat(content).contains("\"language\" : \"fr\"")
    }

    @Then("the document config json should contain the book block with title and author")
    fun documentConfigJsonShouldContainBookBlockWithTitleAndAuthor() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"book\"")
        assertThat(content).contains("DSL Book")
        assertThat(content).contains("DSL Author")
        assertThat(content).contains("pages")
        assertThat(content).contains("photos")
    }

    @Then("the document config json should contain default enrich flags all false")
    fun documentConfigJsonShouldContainDefaultEnrichFlagsAllFalse() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"plantuml\" : false")
        assertThat(content).contains("\"images\" : false")
        assertThat(content).contains("\"passthrough\" : false")
    }

    @Then("the document config json should contain default outputs html only")
    fun documentConfigJsonShouldContainDefaultOutputsHtmlOnly() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"html\" : true")
        assertThat(content).contains("\"pdf\" : false")
        assertThat(content).contains("\"epub\" : false")
    }

    @Then("the document config json should contain default metadata language fr")
    fun documentConfigJsonShouldContainDefaultMetadataLanguageFr() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val content = cfg!!.readText()
        assertThat(content).contains("\"language\" : \"fr\"")
    }

    @Then("the two document config json outputs must be byte-identical")
    fun theTwoDocumentConfigJsonOutputsMustBeByteIdentical() {
        val cfg = world.documentConfigJsonFile()
        assertThat(cfg).exists()
        val secondJson = cfg!!.readText()
        val firstJson = world.lastConfigJson
        assertThat(firstJson).isNotNull()
        assertThat(secondJson).isEqualTo(firstJson)
    }

    // --- DOC-12 round-trip extension : deserializeDocumentConfig task ---

    @When("I am executing the serializeDocumentConfig then deserializeDocumentConfig tasks")
    fun executeSerializeThenDeserialize() {
        world.executeGradle("serializeDocumentConfig")
        val cfg = world.documentConfigJsonFile()
        world.lastConfigJson = cfg?.readText()
        world.executeGradle("deserializeDocumentConfig")
    }

    @Then("the round-tripped document config json file should exist")
    fun roundTrippedConfigJsonFileShouldExist() {
        val cfg = world.roundTrippedConfigJsonFile()
        assertThat(cfg).exists()
    }

    @Then("the round-tripped document config json should be byte-identical to the source")
    fun roundTrippedConfigJsonShouldBeByteIdenticalToSource() {
        val roundTripped = world.roundTrippedConfigJsonFile()
        assertThat(roundTripped).exists()
        val roundTrippedJson = roundTripped!!.readText()
        val sourceJson = world.lastConfigJson
        assertThat(sourceJson).isNotNull()
        assertThat(roundTrippedJson).isEqualTo(sourceJson)
    }

    @Given("a new document project with a git repository")
    fun createNewDocumentProjectWithGitRepo() {
        world.createGradleProjectWithGitRepo()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a git repository and markdown renderer")
    fun createNewDocumentProjectWithGitRepoAndMarkdownRenderer() {
        world.createGradleProjectWithGitRepoAndMarkdownRenderer()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a git repository and custom categories")
    fun createNewDocumentProjectWithGitRepoAndCustomCategories() {
        world.createGradleProjectWithGitRepoAndCustomCategories()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a git repository and ollama-asciidoc renderer with fake LLM")
    fun createNewDocumentProjectWithGitRepoAndOllamaAsciidocRenderer() {
        world.createGradleProjectWithGitRepoAndOllamaAsciidocRendererWithFakeLlm()
        assertThat(world.projectDir).exists()
    }

    @Given("a conventional commit {string}")
    fun addConventionalCommit(message: String) {
        world.gitCommit(message)
    }

    @Given("a git tag {string}")
    fun addGitTag(name: String) {
        world.gitTag(name)
    }

    @Then("the release notes adoc file should exist")
    fun releaseNotesAdocShouldExist() {
        val file = world.releaseNotesAdocFile()
        assertThat(file).exists()
    }

    @Then("the release notes adoc should contain {string}")
    fun releaseNotesAdocShouldContain(text: String) {
        val file = world.releaseNotesAdocFile()
        assertThat(file).exists()
        assertThat(file!!.readText()).contains(text)
    }

    @Then("the release notes adoc should have the version {string} in its filename")
    fun releaseNotesAdocShouldHaveVersionInFilename(version: String) {
        val dir = world.releaseNotesOutputDir()
        assertThat(dir).exists()
        val files = dir!!.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertThat(files.any { it.name.contains(version) }).isTrue()
    }

    @Then("the release notes markdown file should exist")
    fun releaseNotesMarkdownFileShouldExist() {
        val file = world.releaseNotesMarkdownFile()
        assertThat(file).exists()
    }

    @Then("the release notes markdown should contain {string}")
    fun releaseNotesMarkdownShouldContain(text: String) {
        val file = world.releaseNotesMarkdownFile()
        assertThat(file).exists()
        assertThat(file!!.readText()).contains(text)
    }

    // --- DOC-8.3 — N3 metadata integration ---

    @Then("the composite context json should contain the release notes entry")
    fun compositeContextJsonShouldContainReleaseNotesEntry() {
        val composite = world.compositeContextJsonFile()
        assertThat(composite).exists()
        val content = composite!!.readText()
        assertThat(content).contains("\"releaseNotes\"")
        assertThat(content).contains("\"releaseNotesCount\"")
        assertThat(content).contains("release-notes")
        assertThat(content).contains("\"rendererType\"")
    }

    @Then("the metadata json should contain the release notes path")
    fun metadataJsonShouldContainReleaseNotesPath() {
        val metadata = world.metadataJsonFile()
        assertThat(metadata).exists()
        val content = metadata!!.readText()
        assertThat(content).contains("\"releaseNotesPath\"")
        assertThat(content).contains("release-notes")
    }

    @Then("the metadata json should contain the release notes renderer asciidoc")
    fun metadataJsonShouldContainReleaseNotesRendererAsciidoc() {
        val metadata = world.metadataJsonFile()
        assertThat(metadata).exists()
        val content = metadata!!.readText()
        assertThat(content).contains("\"releaseNotesRenderer\"")
        assertThat(content).contains("asciidoc")
    }

    @Then("the metadata json should not contain release notes path")
    fun metadataJsonShouldNotContainReleaseNotesPath() {
        val metadata = world.metadataJsonFile()
        assertThat(metadata).exists()
        val content = metadata!!.readText()
        assertThat(content).doesNotContain("releaseNotesPath")
        assertThat(content).doesNotContain("releaseNotesRenderer")
    }

    // --- DOC-12 extension — book { } nested DSL ---

    @Given("a new document project with a unified DSL book block")
    fun createNewDocumentProjectWithUnifiedBookBlock() {
        world.createGradleProjectWithUnifiedBookBlock()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a unified DSL book block with photos")
    fun createNewDocumentProjectWithUnifiedBookBlockWithPhotos() {
        world.createGradleProjectWithUnifiedBookBlock(photos = true)
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a unified DSL book block without title and author")
    fun createNewDocumentProjectWithUnifiedBookBlockWithoutTitleAndAuthor() {
        world.createGradleProjectWithUnifiedBookBlock(titleAndAuthor = false)
        assertThat(world.projectDir).exists()
    }

    // --- DOC-9b — image:: directives rendering in final output ---

    @Given("a new document project with an AsciiDoc source containing an image directive")
    fun createNewDocumentProjectWithImageDirective() {
        world.createGradleProjectWithAsciiDocSourceContainingImage()
        assertThat(world.projectDir).exists()
    }

    @Then("the converted HTML should render an img tag for the image directive")
    fun convertedHtmlShouldRenderImgTagForImageDirective() {
        val html = world.convertedHtmlFile()
        assertThat(html).exists()
        val content = html!!.readText()
        assertThat(content).contains("<img")
        assertThat(content).contains("photo.png")
    }

    @Then("the assembled book should contain the DSL book title")
    fun assembledBookShouldContainDslBookTitle() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains("= DSL Book")
    }

    @Then("the assembled book should contain the DSL book author")
    fun assembledBookShouldContainDslBookAuthor() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains(":author: DSL Author")
    }

    @Then("the assembled book should contain the default book title")
    fun assembledBookShouldContainDefaultBookTitle() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains("= Untitled Book")
    }

    @Then("the assembled book should contain the default book author")
    fun assembledBookShouldContainDefaultBookAuthor() {
        val book = world.assembledBookFile()
        assertThat(book).exists()
        val content = book!!.readText()
        assertThat(content).contains(":author: Unknown Author")
    }

    @After
    fun cleanup() {
        world.cleanup()
    }

    @Given("a new document project with a template DSL")
    fun createNewDocumentProjectWithTemplateDsl() {
        world.createGradleProjectWithTemplateDsl()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a template DSL and a missing variable")
    fun createNewDocumentProjectWithTemplateDslAndMissingVariable() {
        world.createGradleProjectWithTemplateDslAndMissingVariable()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a template DSL and failOnMissingVariable set to false")
    fun createNewDocumentProjectWithTemplateDslAndFailOnMissingFalse() {
        world.createGradleProjectWithTemplateDslAndFailOnMissingFalse()
        assertThat(world.projectDir).exists()
    }

    @Given("a new document project with a batch DSL")
    fun createNewDocumentProjectWithBatchDsl() {
        world.createGradleProjectWithBatchDsl()
        assertThat(world.projectDir).exists()
    }
}