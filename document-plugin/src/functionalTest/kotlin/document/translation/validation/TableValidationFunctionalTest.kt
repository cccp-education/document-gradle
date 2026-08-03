package document.translation.validation

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class TableValidationFunctionalTest {

    private fun newTempDir(): File = Files.createTempDirectory("doc-tv-ft").toFile()

    private fun setupTestProject(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-tv\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `translateDocument with tableValidation LENIENT succeeds on corrupted table`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)
        projectDir.resolve("source.adoc").writeText(
            """title=Test
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
| Valeur 1 | Valeur 2
|===
"""
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "translateDocument",
                "-Pdocument.translateSource=source.adoc",
                "-Pdocument.translateLlmMode=fake",
                "-Pdocument.translateTableValidation=LENIENT",
            )
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":translateDocument")?.outcome)
    }

    @Test
    fun `translateDocument with tableValidation STRICT succeeds on valid table`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)
        projectDir.resolve("source.adoc").writeText(
            """title=Test
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
| Valeur 1 | Valeur 2
|===
"""
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "translateDocument",
                "-Pdocument.translateSource=source.adoc",
                "-Pdocument.translateLlmMode=fake",
                "-Pdocument.translateTableValidation=STRICT",
            )
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":translateDocument")?.outcome)
    }

    @Test
    fun `translateDocument with tableValidation OFF skips validation`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)
        projectDir.resolve("source.adoc").writeText(
            """title=Test
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
| Valeur 1 | Valeur 2
|===
"""
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "translateDocument",
                "-Pdocument.translateSource=source.adoc",
                "-Pdocument.translateLlmMode=fake",
                "-Pdocument.translateTableValidation=OFF",
            )
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":translateDocument")?.outcome)
    }
}
