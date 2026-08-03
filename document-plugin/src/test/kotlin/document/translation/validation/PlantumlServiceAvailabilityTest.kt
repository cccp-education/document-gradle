package document.translation.validation

import org.junit.jupiter.api.Test
import plantuml.service.PlantumlService
import plantuml.validation.SyntaxValidationResult
import kotlin.test.assertIs

class PlantumlServiceAvailabilityTest {

    @Test
    fun `PlantumlService is instantiable and validates syntax`() {
        val service = PlantumlService()

        val result = service.validateSyntax(
            """
            @startuml
            class Test
            @enduml
            """.trimIndent()
        )

        assertIs<SyntaxValidationResult.Valid>(result)
    }
}
