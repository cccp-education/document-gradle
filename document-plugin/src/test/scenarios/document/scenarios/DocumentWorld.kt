package document.scenarios

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.File
import java.nio.file.Files

class DocumentWorld {

    var projectDir: File? = null
    var buildResult: BuildResult? = null

    fun createGradleProject(): File {
        val dir = Files.createTempDirectory("doc-bdd").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithFakeLlm(existingOutput: Boolean = false): File {
        val dir = Files.createTempDirectory("doc-bdd-fake").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                llmMode.set("fake")
                prompt.set("Genere une introduction pour un plugin Gradle documentaire.")
                systemPrompt.set("Tu es un redacteur technique expert en AsciiDoc.")
            }
            """.trimIndent()
        )
        if (existingOutput) {
            val outDir = dir.resolve("build/docs/document")
            outDir.mkdirs()
            dir.resolve("build/docs/document/document.adoc").writeText(
                "// document-gradle generated — prompt hash: placeholder\n= Document Existant\n"
            )
        }
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocSource(existingHtmlOutput: Boolean = false): File {
        val dir = Files.createTempDirectory("doc-bdd-html").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers HTML5.
            """.trimIndent()
        )
        if (existingHtmlOutput) {
            val outDir = dir.resolve("build/docs/document")
            outDir.mkdirs()
            dir.resolve("build/docs/document/document.html").writeText(
                "<!DOCTYPE html>\n<html><body>placeholder</body></html>\n"
            )
        }
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocSourceAndExistingPdf(): File {
        val dir = Files.createTempDirectory("doc-bdd-pdf").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers PDF.
            """.trimIndent()
        )
        val outDir = dir.resolve("build/docs/document")
        outDir.mkdirs()
        dir.resolve("build/docs/document/document.pdf").writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        projectDir = dir
        return dir
    }

    fun convertedHtmlFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.html")
    }

    fun convertedPdfFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.pdf")
    }

    fun executeGradle(vararg tasks: String): BuildResult {
        require(projectDir != null) { "Project directory must be initialized" }
        return try {
            GradleRunner.create()
                .withProjectDir(projectDir!!)
                .withArguments(tasks.toList() + "--stacktrace")
                .withPluginClasspath()
                .build()
                .also { buildResult = it }
        } catch (e: UnexpectedBuildFailure) {
            e.buildResult.also { buildResult = it }
        }
    }

    fun generatedDocument(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.adoc")
    }

    fun cleanup() {
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
    }
}