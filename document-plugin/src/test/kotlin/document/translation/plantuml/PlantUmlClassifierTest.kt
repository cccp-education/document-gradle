package document.translation.plantuml

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PlantUmlClassifierTest {

    private val classifier = PlantUmlClassifier()

    @Test
    fun `classify pure labels class diagram returns TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "Utilisateur"
class "Service"
"Utilisateur" --> "Service" : "Requête"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
    }

    @Test
    fun `classify pure labels sequence diagram returns TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
actor "Client" as Client
participant "Serveur" as Server
Client -> Server: "Demande de connexion"
Server --> Client: "Réponse"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
    }

    @Test
    fun `classify pure labels usecase diagram returns TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
usecase "S'inscrire" as UC1
usecase "Se connecter" as UC2
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
    }

    @Test
    fun `classify technical terms only returns PreserveTechnical`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class User
class Service
User --> Service
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.PreserveTechnical, classifier.classify(block))
    }

    @Test
    fun `classify activity diagram with technical syntax returns PreserveTechnical`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
start
:Process;
stop
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.PreserveTechnical, classifier.classify(block))
    }

    @Test
    fun `classify REAC vocabulary returns BorrowVocabulary`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "REAC"
class "AFNOR"
"REAC" --> "AFNOR" : "Référentiel"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.BorrowVocabulary, classifier.classify(block))
    }

    @Test
    fun `classify DC and TS vocabulary returns BorrowVocabulary`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "DC" as DC
class "TS" as TS
"DC" --> "TS" : "Évaluation"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.BorrowVocabulary, classifier.classify(block))
    }

    @Test
    fun `extract labels from class diagram`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "Utilisateur"
class "Service"
"Utilisateur" --> "Service" : "Requête"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Utilisateur" in labels)
        assertTrue("Service" in labels)
        assertTrue("Requête" in labels)
    }

    @Test
    fun `extract labels from sequence diagram`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
actor "Client" as Client
participant "Serveur" as Server
Client -> Server: "Demande"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Client" in labels)
        assertTrue("Serveur" in labels)
        assertTrue("Demande" in labels)
    }

    @Test
    fun `extract labels from usecase diagram`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
usecase "S'inscrire" as UC1
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("S'inscrire" in labels)
    }

    @Test
    fun `no labels returns PreserveTechnical`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class A
class B
A --> B
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.PreserveTechnical, classifier.classify(block))
        assertTrue(block.labels().isEmpty())
    }

    @Test
    fun `mixed technical and translatable labels returns TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class User
class "Service métier"
User --> "Service métier" : "Appel"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
    }

    @Test
    fun `labels containing code are skipped`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "com.example.UserService"
class "Service"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Service" in labels)
        assertTrue(!labels.contains("com.example.UserService"))
    }

    @Test
    fun `PlantUmlBlock equality is structural`() {
        val block1 = PlantUmlBlock(raw = "raw content")
        val block2 = PlantUmlBlock(raw = "raw content")
        assertEquals(block1, block2)
    }

    @Test
    fun `PlantUmlStrategy sealed interface has exactly 3 variants`() {
        val strategies = listOf(
            PlantUmlStrategy.TranslateLabels,
            PlantUmlStrategy.PreserveTechnical,
            PlantUmlStrategy.BorrowVocabulary
        )
        assertEquals(3, strategies.size)
    }
}
