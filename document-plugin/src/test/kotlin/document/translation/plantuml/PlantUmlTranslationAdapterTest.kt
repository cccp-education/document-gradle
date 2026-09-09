package document.translation.plantuml

import document.translation.PivotBlock
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlantUmlTranslationAdapterTest {

    private fun fakeTranslator(prefix: String = "[EN]"): TranslationService = object : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            TranslationResult.Success("${prefix} ${request.sourceText}")
    }

    private fun preservingOnlyLabelsTranslator(): TranslationService = object : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult {
            val text = request.sourceText
            val translated = text
                .replace("Utilisateur", "User")
                .replace("Service", "Service")
                .replace("Client", "Customer")
                .replace("Serveur", "Server")
                .replace("Requête", "Request")
                .replace("Demande", "Request")
                .replace("Réponse", "Response")
                .replace("S'inscrire", "Sign up")
            return TranslationResult.Success(translated)
        }
    }

    private fun plantumlSource(content: String): PivotBlock.Source =
        PivotBlock.Source(language = "plantuml", content = content)

    @Test
    fun `non-plantuml source is returned unchanged`() {
        val adapter = PlantUmlTranslationAdapter(fakeTranslator())
        val block = PivotBlock.Source(language = "kotlin", content = "val x = 1")
        assertThat(adapter.translate(block, "fr", "en")).isSameAs(block)
    }

    @Test
    fun `PreserveTechnical returns block unchanged`() {
        val adapter = PlantUmlTranslationAdapter(fakeTranslator())
        val block = plantumlSource(
            """
            @startuml
            class User
            class Service
            User --> Service
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).isEqualTo(block.content)
    }

    @Test
    fun `TranslateLabels translates quoted labels`() {
        val adapter = PlantUmlTranslationAdapter(preservingOnlyLabelsTranslator())
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            class "Service"
            "Utilisateur" --> "Service" : "Requête"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"User\"")
        assertThat(result.content).contains("\"Request\"")
        assertThat(result.content).doesNotContain("\"Utilisateur\"")
    }

    @Test
    fun `TranslateLabels keeps block structure intact`() {
        val adapter = PlantUmlTranslationAdapter(preservingOnlyLabelsTranslator())
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("@startuml")
        assertThat(result.content).contains("@enduml")
        assertThat(result.content).contains("class")
    }

    @Test
    fun `BorrowVocabulary preserves injected term verbatim`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = preservingOnlyLabelsTranslator(),
            borrowedVocabulary = setOf("REF", "ORG"),
        )
        val block = plantumlSource(
            """
            @startuml
            class "REF"
            class "ORG"
            "REF" --> "ORG" : "Référentiel"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"REF\"")
        assertThat(result.content).contains("\"ORG\"")
        assertThat(result.content).doesNotContain("\"[EN] Référentiel\"")
    }

    @Test
    fun `BorrowVocabulary translates non-vocabulary labels with injected config`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = preservingOnlyLabelsTranslator(),
            borrowedVocabulary = setOf("REF"),
        )
        val block = plantumlSource(
            """
            @startuml
            class "REF"
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"REF\"")
        assertThat(result.content).contains("\"User\"")
        assertThat(result.content).doesNotContain("\"Utilisateur\"")
    }

    @Test
    fun `unconfigured vocabulary terms are translated by default`() {
        val adapter = PlantUmlTranslationAdapter(fakeTranslator())
        val block = plantumlSource(
            """
            @startuml
            class "REF"
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"[EN] REF\"")
        assertThat(result.content).contains("\"[EN] Utilisateur\"")
    }

    @Test
    fun `TranslateLabels skips labels containing code`() {
        val adapter = PlantUmlTranslationAdapter(preservingOnlyLabelsTranslator())
        val block = plantumlSource(
            """
            @startuml
            class "com.example.UserService"
            class "Service"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"com.example.UserService\"")
    }

    @Test
    fun `PreserveTechnical round-trip leaves content identical`() {
        val adapter = PlantUmlTranslationAdapter(fakeTranslator())
        val content = """
            @startuml
            start
            :Process;
            stop
            @enduml
        """.trimIndent()
        val block = plantumlSource(content)
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).isEqualTo(content)
    }

    @Test
    fun `BorrowVocabulary preserves DC and TS terms`() {
        val adapter = PlantUmlTranslationAdapter(
            translationService = preservingOnlyLabelsTranslator(),
            borrowedVocabulary = setOf("MOD", "EVA"),
        )
        val block = plantumlSource(
            """
            @startuml
            class "MOD" as MOD
            class "EVA" as EVA
            "MOD" --> "EVA" : "Évaluation"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"MOD\"")
        assertThat(result.content).contains("\"EVA\"")
    }

    @Test
    fun `TranslateLabels returns same language when translator fails`() {
        val failing = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult =
                TranslationResult.Failure("boom")
        }
        val adapter = PlantUmlTranslationAdapter(failing)
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.content).contains("\"Utilisateur\"")
    }

    @Test
    fun `translate produces new Source instance preserving language`() {
        val adapter = PlantUmlTranslationAdapter(preservingOnlyLabelsTranslator())
        val block = plantumlSource(
            """
            @startuml
            class "Utilisateur"
            @enduml
            """.trimIndent()
        )
        val result = adapter.translate(block, "fr", "en")
        assertThat(result.language).isEqualTo("plantuml")
    }
}
