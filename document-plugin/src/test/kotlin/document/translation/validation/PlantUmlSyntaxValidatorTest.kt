package document.translation.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlantUmlSyntaxValidatorTest {

    private val validator = PlantUmlSyntaxValidator.create()

    @Test
    fun `valid simple diagram returns Valid`() {
        val code = """
            @startuml
            class User
            class Service
            User --> Service
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "My Article", 0, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Valid>(result)
    }

    @Test
    fun `valid diagram with notes returns Valid`() {
        val code = """
            @startuml
            class User
            note right of User
              A user entity
            end note
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "Article", 1, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Valid>(result)
    }

    @Test
    fun `valid diagram with sequence returns Valid`() {
        val code = """
            @startuml
            Alice -> Bob: Hello
            Bob --> Alice: Hi
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "Article", 0, "BorrowVocabulary")

        assertIs<PlantUmlValidationResult.Valid>(result)
    }

    @Test
    fun `valid diagram with activity returns Valid`() {
        val code = """
            @startuml
            start
            :Process;
            stop
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "Article", 0, "PreserveTechnical")

        assertIs<PlantUmlValidationResult.Valid>(result)
    }

    @Test
    fun `missing startuml returns Invalid`() {
        val code = """
            class User
            class Service
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "My Article", 2, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Invalid>(result)
        assertEquals("My Article", result.articleTitle)
        assertEquals(2, result.blockIndex)
        assertEquals("TranslateLabels", result.strategy)
        assertTrue(result.reason.contains("@startuml"))
    }

    @Test
    fun `missing enduml returns Invalid`() {
        val code = """
            @startuml
            class User
            class Service
        """.trimIndent()

        val result = validator.validate(code, "Article", 0, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("@enduml"))
    }

    @Test
    fun `empty diagram returns Invalid`() {
        val code = ""

        val result = validator.validate(code, "Article", 0, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("@startuml"))
    }

    @Test
    fun `strategy context is propagated in Invalid result`() {
        val code = "not a diagram"

        val result = validator.validate(code, "Specific Article", 3, "BorrowVocabulary")

        assertIs<PlantUmlValidationResult.Invalid>(result)
        assertEquals("Specific Article", result.articleTitle)
        assertEquals(3, result.blockIndex)
        assertEquals("BorrowVocabulary", result.strategy)
    }

    @Test
    fun `PreserveTechnical strategy is propagated in Invalid result`() {
        val code = "garbage"

        val result = validator.validate(code, "Article", 0, "PreserveTechnical")

        assertIs<PlantUmlValidationResult.Invalid>(result)
        assertEquals("PreserveTechnical", result.strategy)
    }

    @Test
    fun `complex diagram with multiple elements returns Valid`() {
        val code = """
            @startuml
            package "Core" {
              class User
              class Service
            }
            package "API" {
              class Controller
            }
            User --> Service
            Controller --> Service
            @enduml
        """.trimIndent()

        val result = validator.validate(code, "Article", 0, "TranslateLabels")

        assertIs<PlantUmlValidationResult.Valid>(result)
    }
}
