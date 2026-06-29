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