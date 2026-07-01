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

    fun createGradleProjectWithAsciiDocSourceAndExistingEpub(): File {
        val dir = Files.createTempDirectory("doc-bdd-epub").toFile()
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

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers EPUB3.
            """.trimIndent()
        )
        val outDir = dir.resolve("build/docs/document")
        outDir.mkdirs()
        // EPUB est un zip — signature PK\x03\x04
        dir.resolve("build/docs/document/document.epub").writeBytes(
            byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        )
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocManpageSource(): File {
        val dir = Files.createTempDirectory("doc-bdd-man").toFile()
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
        // AsciiDoc manpage — doctype manpage + section 1
        dir.resolve("source.adoc").writeText(
            """
            = document(1)
            :doctype: manpage
            :manmanual: Document Gradle Manual
            :mansource: Document Gradle

            == NAME

            document - Gradle plugin for AsciiDoc document creation and publication

            == SYNOPSIS

            *document* ['OPTION']...

            == DESCRIPTION

            The *document* plugin converts AsciiDoc to multiple formats.

            == OPTIONS

            *-h, --help*::
            Print help message.
            """.trimIndent()
        )
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocSourceContainingPlantuml(): File {
        val dir = Files.createTempDirectory("doc-bdd-puml").toFile()
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
                enrichPlantUml.set(true)
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText(
            """
            = Document avec Diagramme

            == Architecture

            [plantuml]
            ----
            @startuml
            Alice -> Bob: hello
            Bob -> Alice: hi
            @enduml
            ----
            """.trimIndent()
        )
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocSourceContainingPassthrough(): File {
        val dir = Files.createTempDirectory("doc-bdd-pass").toFile()
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
                enrichPassthrough.set(true)
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText(
            """
            = Document avec HTML brut

            == Introduction

            ++++
            <iframe src="https://example.com" width="600" height="400"></iframe>
            ++++

            Paragraphe suivant.
            """.trimIndent()
        )
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithAsciiDocSourceContainingInclude(): File {
        val dir = Files.createTempDirectory("doc-bdd-inc").toFile()
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
        dir.resolve("chapter.adoc").writeText("== Chapitre Inclu\n\nTexte du chapitre inclus.")
        dir.resolve("source.adoc").writeText(
            """
            = Document avec Include

            include::chapter.adoc[]
            """.trimIndent()
        )
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

    fun convertedEpubFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.epub")
    }

    fun convertedDocBookFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.xml")
    }

    fun convertedManPageFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document.man")
    }

    fun enrichedDocumentFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document-enriched.adoc")
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