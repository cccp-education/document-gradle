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
    fun `classify injected vocabulary returns BorrowVocabulary`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "REF"
class "ORG"
"REF" --> "ORG" : "Référentiel"
@enduml
----""",
            borrowedVocabulary = setOf("REF", "ORG")
        )
        assertEquals(PlantUmlStrategy.BorrowVocabulary, classifier.classify(block))
    }

    @Test
    fun `classify without injected vocabulary falls back to TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "REF"
class "ORG"
"REF" --> "ORG" : "Référentiel"
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
    }

    @Test
    fun `classify DC and TS vocabulary returns BorrowVocabulary`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
class "MOD" as MOD
class "EVA" as EVA
"MOD" --> "EVA" : "Évaluation"
@enduml
----""",
            borrowedVocabulary = setOf("MOD", "EVA")
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
    fun `extract the title directive as a translatable label`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
title Évolution des Sessions — De la Session 1 à 150+
class "Utilisateur"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Évolution des Sessions — De la Session 1 à 150+" in labels, "labels=$labels")
    }

    @Test
    fun `extract header and footer directives as translatable labels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
header Architecture Eager/Lazy
footer Document de référence
class "Utilisateur"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Architecture Eager/Lazy" in labels, "labels=$labels")
        assertTrue("Document de référence" in labels, "labels=$labels")
    }

    @Test
    fun `extract the caption directive as a translatable label`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
caption Schéma de gouvernance
class "Utilisateur"
@enduml
----"""
        )
        val labels = block.labels()
        assertTrue("Schéma de gouvernance" in labels, "labels=$labels")
    }

    @Test
    fun `a bare title without any quoted label still yields TranslateLabels`() {
        val block = PlantUmlBlock(
            raw = """[plantuml]
----
@startuml
title Convention de Nommage des Sessions
class A
class B
A --> B
@enduml
----"""
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, classifier.classify(block))
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
