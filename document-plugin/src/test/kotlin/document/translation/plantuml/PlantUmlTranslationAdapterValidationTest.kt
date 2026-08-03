package document.translation.plantuml

import document.translation.PivotBlock
import document.translation.TranslationException
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.ValidationMode
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PlantUmlTranslationAdapterValidationTest {

    private fun fakeTranslator(prefix: String = "[EN]"): TranslationService = object : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            TranslationResult.Success("$prefix ${request.sourceText}")
    }

    private fun plantumlSource(content: String): PivotBlock.Source =
        PivotBlock.Source(language = "plantuml", content = content)

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
    fun `valid diagram after translation passes validation`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            class "Service"
            "Utilisateur" --> "Service"
            @enduml
            """.trimIndent()
        )

        val result = adapter.translate(block, "fr", "en", "My Article", 0)

        assertNotNull(result)
        assertTrue(adapter.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `corrupted diagram after translation produces Invalid in lenient mode`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )

        val result = adapter.translate(block, "fr", "en", "My Article", 2)

        assertNotNull(result)
        assertEquals(1, adapter.plantUmlValidationResults.size)
        val invalid = adapter.plantUmlValidationResults.first()
        assertEquals("My Article", invalid.articleTitle)
        assertEquals(2, invalid.blockIndex)
        assertTrue(invalid.reason.contains("Simulated corruption"))
    }

    @Test
    fun `corrupted diagram throws TranslationException in strict mode`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.STRICT,
        )
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )

        try {
            adapter.translate(block, "fr", "en", "My Article", 0)
            fail("Expected TranslationException")
        } catch (e: TranslationException) {
            assertTrue(e.message!!.contains("PlantUML validation failed"))
            assertTrue(e.message!!.contains("My Article"))
        }
    }

    @Test
    fun `PreserveTechnical strategy skips validation`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val block = plantumlSource(
            """
            @startuml
            class User
            class Service
            User --> Service
            @enduml
            """.trimIndent()
        )

        val result = adapter.translate(block, "fr", "en", "Article", 0)

        assertNotNull(result)
        assertTrue(adapter.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `BorrowVocabulary validates after translation`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val block = plantumlSource(
            """
            @startuml
            class "REAC"
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )

        val result = adapter.translate(block, "fr", "en", "Article", 1)

        assertNotNull(result)
        assertEquals(1, adapter.plantUmlValidationResults.size)
    }

    @Test
    fun `OFF mode skips validation entirely`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.OFF,
        )
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )

        val result = adapter.translate(block, "fr", "en", "Article", 0)

        assertNotNull(result)
        assertTrue(adapter.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `non-plantuml block is returned unchanged and not validated`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.STRICT,
        )
        val block = PivotBlock.Source(language = "kotlin", content = "val x = 1")

        val result = adapter.translate(block, "fr", "en", "Article", 0)

        assertIs<PivotBlock.Source>(result)
        assertEquals("val x = 1", result.content)
        assertTrue(adapter.plantUmlValidationResults.isEmpty())
    }

    @Test
    fun `strategy context is propagated in validation result`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = fakeTranslator(),
            plantUmlValidator = alwaysInvalidValidator(),
            plantUmlValidationMode = ValidationMode.LENIENT,
        )
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )

        adapter.translate(block, "fr", "en", "Specific Article", 3)

        assertEquals(1, adapter.plantUmlValidationResults.size)
        val invalid = adapter.plantUmlValidationResults.first()
        assertEquals("Specific Article", invalid.articleTitle)
        assertEquals(3, invalid.blockIndex)
        assertTrue(invalid.strategy.contains("TranslateLabels"))
    }
}
