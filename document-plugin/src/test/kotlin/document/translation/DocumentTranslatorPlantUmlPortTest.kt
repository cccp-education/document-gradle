package document.translation

import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * US-4 PLT-DIAGRAM-OWNERSHIP (option A) — `DocumentTranslator` can delegate
 * PlantUML blocks to the N0 `PlantUmlTranslationPort` instead of its private
 * adapter. bakery (the orchestrator) builds the port implementation
 * (`PlantumlTranslationPortAdapter`) and injects it here.
 *
 * Additive seam: the private adapter path is preserved (backward compat); when a
 * port is wired, it takes precedence.
 */
class DocumentTranslatorPlantUmlPortTest {

    private val fakeService = FakeTranslationService(" [EN]")

    private val echoPort =
        object : PlantUmlTranslationPort {
            override fun translate(request: PlantUmlTranslationRequest): PlantUmlTranslationOutcome =
                PlantUmlTranslationOutcome.Translated(
                    request.block.copy(raw = request.block.raw.replace("\"Utilisateur\"", "\"User\"")),
                )
        }

    @Test
    fun `translate delegates plantuml blocks to the N0 port when wired`() {
        val translator = DocumentTranslator(fakeService, plantUmlPort = echoPort)
        val source = """title=Test

[plantuml]
----
@startuml
class "Utilisateur"
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("\"User\""), "the N0 port must have translated the label")
    }

    @Test
    fun `a preserved outcome keeps the source block verbatim`() {
        val preservingPort =
            object : PlantUmlTranslationPort {
                override fun translate(request: PlantUmlTranslationRequest): PlantUmlTranslationOutcome =
                    PlantUmlTranslationOutcome.Preserved("semantic identity")
            }
        val translator = DocumentTranslator(fakeService, plantUmlPort = preservingPort)
        val source = """title=Test

[plantuml]
----
@startuml
class "Utilisateur"
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("\"Utilisateur\""), "a PRESERVE outcome must keep the source label")
    }
}
