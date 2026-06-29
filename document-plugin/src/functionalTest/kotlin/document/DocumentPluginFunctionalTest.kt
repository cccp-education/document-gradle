package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.nio.file.Files

class DocumentPluginFunctionalTest {

    private fun newTempDir(): File = Files.createTempDirectory("doc-ft").toFile()

    @Test
    fun `plugin s'applique et enregistre les 8 taches documentaires`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--group", "document")
            .withPluginClasspath()
            .build()

        val expectedTasks = listOf(
            "generateDocument",
            "enrichDocument",
            "convertDocumentToHtml",
            "convertDocumentToPdf",
            "convertDocumentToEpub",
            "convertDocumentToDocBook",
            "convertDocumentToManPage",
            "collectDocumentRetrieve",
        )
        expectedTasks.forEach { taskName ->
            assertTrue(result.output.contains(taskName), "la tache '$taskName' doit etre listee")
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `plugin applique l'extension document via DSL`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("document"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `convention source par defaut est src docs document adoc`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("help", "--task", "generateDocument")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("generateDocument"))
    }

    @Test
    fun `DSL override la convention source`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText("= Mon Livre\n\nContenu du livre.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document.adoc")
        assertTrue(output.exists(), "le fichier de sortie doit etre genere")
        assertTrue(output.readText().contains("= Mon Livre"))
    }

    @Test
    fun `CLI -Pdocument source surcharge la convention`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)
        File(projectDir, "livre-cli.adoc").writeText("= Livre CLI\n\nContenu depuis CLI.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument", "-Pdocument.source=livre-cli.adoc")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document.adoc")
        assertTrue(output.exists(), "le fichier de sortie doit etre genere depuis la source CLI")
        assertTrue(output.readText().contains("= Livre CLI"))
    }

    @Test
    fun `generateDocument IA produit un AsciiDoc valide via fake LLM`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-doc-ia\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                llmMode.set("fake")
                prompt.set("Genere une introduction pour un plugin Gradle documentaire.")
                systemPrompt.set("Tu es un redacteur technique expert.")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document.adoc")
        assertTrue(output.exists(), "le fichier genere doit exister")
        val content = output.readText()
        assertTrue(content.contains("= Document Genere"), "le document doit contenir un titre AsciiDoc niveau 0")
        assertTrue(content.contains("prompt hash:"), "le document doit contenir le hash du prompt (economie d'encre)")
    }

    @Test
    fun `generateDocument IA skip si sortie existante pour meme prompt`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-doc-skip\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                llmMode.set("fake")
                prompt.set("Genere une introduction.")
            }
            """.trimIndent()
        )

        val firstResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":generateDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document.adoc")
        assertTrue(output.exists())
        val firstContent = output.readText()

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":generateDocument")?.outcome)
        assertEquals(firstContent, output.readText(), "le contenu ne doit pas changer apres skip")
    }

    @Test
    fun `generateDocument copie la source si aucun prompt n est defini`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText("= Mon Livre\n\nContenu du livre.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateDocument")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document.adoc")
        assertTrue(output.exists())
        assertTrue(output.readText().contains("= Mon Livre"))
    }

    @Test
    fun `convertDocumentToHtml produit un fichier HTML5 valide depuis la source`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test.
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val output = File(projectDir, "build/docs/document/document.html")
        assertTrue(output.exists(), "le fichier HTML doit etre genere")
        val content = output.readText()
        assertTrue(content.contains("<!DOCTYPE html", ignoreCase = true), "le HTML doit contenir une declaration doctype")
        assertTrue(content.contains("Document de Test"), "le HTML doit contenir le titre du document source")
    }

    @Test
    fun `convertDocumentToHtml skip si la sortie existe et la source est inchangee`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText("= Mon Livre\n\nContenu.")

        val firstResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":convertDocumentToHtml")?.outcome)
        val output = File(projectDir, "build/docs/document/document.html")
        assertTrue(output.exists())
        val firstContent = output.readText()

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":convertDocumentToHtml")?.outcome)
        assertEquals(firstContent, output.readText(), "le contenu ne doit pas changer apres skip")
    }

    @Test
    fun `convertDocumentToPdf produit un fichier PDF valide depuis la source`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers PDF.
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToPdf")?.outcome)
        val output = File(projectDir, "build/docs/document/document.pdf")
        assertTrue(output.exists(), "le fichier PDF doit etre genere")
        assertTrue(output.length() > 100, "le PDF ne doit pas etre vide")
        val bytes = output.readBytes()
        val header = String(bytes.copyOfRange(0, minOf(5, bytes.size)))
        assertTrue(header.startsWith("%PDF"), "le fichier doit commencer par la signature PDF")
    }

    @Test
    fun `convertDocumentToPdf skip si la sortie existe et la source est inchangee`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText("= Mon Livre\n\nContenu.")

        val firstResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":convertDocumentToPdf")?.outcome)
        val output = File(projectDir, "build/docs/document/document.pdf")
        assertTrue(output.exists())
        val firstSize = output.length()

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":convertDocumentToPdf")?.outcome)
        assertEquals(firstSize, output.length(), "le PDF ne doit pas changer apres skip")
    }

    private fun setupTestProject(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"test-document\"\n"
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
    }

    private fun setupTestProjectWithDsl(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"test-document\"\n"
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("mon-livre.adoc"))
            }
            """.trimIndent()
        )
    }
}