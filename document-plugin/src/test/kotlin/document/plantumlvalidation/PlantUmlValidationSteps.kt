package document.plantumlvalidation

import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import document.translation.TranslationException
import document.translation.plantuml.PlantUmlTranslationAdapter
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.ValidationMode
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlantUmlValidationSteps {

    private var translationService: FakeTranslationService = FakeTranslationService(" [EN]")
    private var translator: DocumentTranslator? = null
    private var plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT
    private var useAlwaysInvalidValidator: Boolean = false
    private var asciidocSource: String = ""
    private var translatedOutput: String? = null
    private var thrownException: Exception? = null

    @Given("a document translator with plantUML validation mode {string}")
    fun `a document translator with plantuml validation mode`(mode: String) {
        plantUmlValidationMode = ValidationMode.valueOf(mode.uppercase())
        useAlwaysInvalidValidator = false
        translationService = FakeTranslationService(" [EN]")
        translator = buildTranslator()
    }

    @And("the plantUML validator always returns invalid")
    fun `the plantuml validator always returns invalid`() {
        useAlwaysInvalidValidator = true
        translator = buildTranslator()
    }

    private fun buildTranslator(): DocumentTranslator {
        val validator = if (useAlwaysInvalidValidator) {
            alwaysInvalidValidator()
        } else {
            PlantUmlSyntaxValidator.create()
        }
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = translationService,
            plantUmlValidator = validator,
            plantUmlValidationMode = plantUmlValidationMode,
        )
        return DocumentTranslator(
            translationService = translationService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = plantUmlValidationMode,
        )
    }

    private fun alwaysInvalidValidator(): PlantUmlSyntaxValidator = object : PlantUmlSyntaxValidator {
        override fun validate(
            plantumlCode: String,
            articleTitle: String,
            blockIndex: Int,
            strategy: String,
        ): PlantUmlValidationResult = PlantUmlValidationResult.Invalid(
            articleTitle = articleTitle,
            blockIndex = blockIndex,
            strategy = strategy,
            reason = "Simulated corruption: unmatched quotes in label",
        )
    }

    @Given("an AsciiDoc article with a valid PlantUML diagram")
    fun `an asciidoc article with a valid plantuml diagram`() {
        asciidocSource = """title=Diagram
date=2026-08-03
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class "Utilisateur"
class "Service"
"Utilisateur" --> "Service"
@enduml
----
"""
    }

    @Given("an AsciiDoc article with a PlantUML diagram containing {string}")
    fun `an asciidoc article with a plantuml diagram containing`(label: String) {
        asciidocSource = """title=Diagram
date=2026-08-03
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class "$label"
@enduml
----
"""
    }

    @Given("an AsciiDoc article with a PlantUML diagram having no translatable labels")
    fun `an asciidoc article with a plantuml diagram having no translatable labels`() {
        asciidocSource = """title=Diagram
date=2026-08-03
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class User
class Service
User --> Service
@enduml
----
"""
    }

    @When("I translate the article with plantUML from {string} to {string}")
    fun `i translate the article with plantuml from source to target`(sourceLang: String, targetLang: String) {
        thrownException = null
        translatedOutput = null
        try {
            translatedOutput = translator!!.translate(asciidocSource, sourceLang, targetLang)
        } catch (e: Exception) {
            thrownException = e
        }
    }

    @Then("the plantUML translation should succeed")
    fun `the plantuml translation should succeed`() {
        assertNotNull(translatedOutput, "Translation should have produced output but threw: ${thrownException?.message}")
    }

    @And("no plantUML validation errors should be reported")
    fun `no plantuml validation errors should be reported`() {
        val results = translator!!.plantUmlValidationResults
        assertTrue(results.isEmpty(), "Expected no plantUML validation errors but got: $results")
    }

    @And("{int} plantUML validation error should be reported")
    fun `plantuml validation error should be reported`(count: Int) {
        val results = translator!!.plantUmlValidationResults
        assertEquals(count, results.size, "Expected $count plantUML validation error(s) but got ${results.size}: $results")
    }

    @And("the plantUML validation error should mention {string}")
    fun `the plantuml validation error should mention`(text: String) {
        val results = translator!!.plantUmlValidationResults
        assertTrue(results.isNotEmpty(), "Expected at least one plantUML validation error")
        val found = results.any { it.reason.contains(text) }
        assertTrue(found, "Expected a plantUML validation error mentioning '$text' but got: ${results.map { it.reason }}")
    }

    @Then("a TranslationException should be thrown for plantUML with message containing {string}")
    fun `a translationexception should be thrown for plantuml with message containing`(text: String) {
        assertNotNull(thrownException, "Expected TranslationException but no exception was thrown")
        assertTrue(
            thrownException is TranslationException,
            "Expected TranslationException but got ${thrownException!!.javaClass.simpleName}",
        )
        assertTrue(
            thrownException!!.message!!.contains(text),
            "Expected message containing '$text' but got: ${thrownException!!.message}",
        )
    }
}
