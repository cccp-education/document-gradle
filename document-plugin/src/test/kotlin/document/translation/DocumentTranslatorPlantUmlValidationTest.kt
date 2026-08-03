package document.translation

import document.translation.plantuml.PlantUmlTranslationAdapter
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.ValidationMode
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DocumentTranslatorPlantUmlValidationTest {

    private val fakeService = FakeTranslationService(" [EN]")

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

    @Test
    fun `plantUML validation in LENIENT mode warns but does not throw`() {
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = fakeService,
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val translator = DocumentTranslator(
            fakeService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = ValidationMode.LENIENT,
        )

        val source = """title=Diagram
date=2026-07-20
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class "Utilisateur"
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("@startuml"))
        assertEquals(1, translator.plantUmlValidationResults.size)
    }

    @Test
    fun `plantUML validation in STRICT mode throws TranslationException`() {
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = fakeService,
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.STRICT,
        )
        val translator = DocumentTranslator(
            fakeService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = ValidationMode.STRICT,
        )

        val source = """title=Diagram
date=2026-07-20
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class "Utilisateur"
@enduml
----
"""

        try {
            translator.translate(source, "fr", "en")
            fail("Expected TranslationException")
        } catch (e: TranslationException) {
            assertTrue(e.message!!.contains("PlantUML validation failed"))
        }
    }

    @Test
    fun `plantUML validation in OFF mode skips validation`() {
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = fakeService,
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.OFF,
        )
        val translator = DocumentTranslator(
            fakeService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = ValidationMode.OFF,
        )

        val source = """title=Diagram
date=2026-07-20
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
class "Utilisateur"
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("@startuml"))
        assertTrue(translator.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `valid plantUML diagram passes validation in LENIENT mode`() {
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = fakeService,
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val translator = DocumentTranslator(
            fakeService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = ValidationMode.LENIENT,
        )

        val source = """title=Diagram
date=2026-07-20
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

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("@startuml"))
        assertTrue(translator.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `multiple plantUML blocks accumulate validation results`() {
        val plantUmlAdapter = PlantUmlTranslationAdapter(
            translationService = fakeService,
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val translator = DocumentTranslator(
            fakeService,
            plantUmlAdapter = plantUmlAdapter,
            plantUmlValidationMode = ValidationMode.LENIENT,
        )

        val source = """title=Diagrams
date=2026-07-20
type=page
status=published
~~~~~~

== First

[plantuml]
----
@startuml
class "A"
@enduml
----

== Second

[plantuml]
----
@startuml
class "B"
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("@startuml"))
        assertEquals(2, translator.plantUmlValidationResults.size)
    }
}
