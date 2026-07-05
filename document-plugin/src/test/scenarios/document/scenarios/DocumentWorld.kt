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

    fun createPublishableGradleProject(): File {
        val dir = Files.createTempDirectory("doc-bdd-pub").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
                `java-gradle-plugin`
                `maven-publish`
            }

            group = "education.cccp"
            version = "0.0.1"

            gradlePlugin {
                plugins {
                    create("document") {
                        id = "education.cccp.document"
                        implementationClass = "document.DocumentPlugin"
                        displayName = "Document Gradle Plugin"
                        description = "Gradle plugin for AsciiDoc document creation and multi-format publication (HTML, PDF, EPUB, DocBook, ManPage) via AsciidoctorJ."
                    }
                }
                website.set("https://github.com/cccp-education/document-gradle/")
                vcsUrl.set("https://github.com/cccp-education/document-gradle.git")
            }

            publishing {
                publications.withType<MavenPublication> {
                    pom {
                        name.set("Document Gradle Plugin")
                        description.set("Gradle plugin for AsciiDoc document creation and multi-format publication (HTML, PDF, EPUB, DocBook, ManPage) via AsciidoctorJ.")
                        developers {
                            developer {
                                id.set("cccp-education")
                                name.set("CCCP Education")
                                email.set("cccp@cccp.education")
                            }
                        }
                        licenses {
                            license {
                                name.set("Apache-2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/cccp-education/document-gradle.git")
                            developerConnection.set("scm:git:ssh://github.com/cccp-education/document-gradle.git")
                            url.set("https://github.com/cccp-education/document-gradle.git")
                        }
                    }
                }
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

    fun createGradleProjectWithAsciiDocSourceAndCustomTheme(): File {
        val dir = Files.createTempDirectory("doc-bdd-theme").toFile()
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
                theme {
                    pdfTheme.set(file("talaria-theme.yml"))
                    htmlStylesheet.set(file("talaria.css"))
                    epubStylesheet.set(file("epub.css"))
                    logo.set(file("logo.png"))
                }
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText(
            """
            = Document Themed

            == Introduction

            Document avec theme custom pour la conversion multi-format.
            """.trimIndent()
        )
        // Fichiers theme fournis (vides — AsciidoctorJ ignore les absents avec warning)
        dir.resolve("talaria-theme.yml").writeText(
            """
            extends: default
            page:
              size: A4
            """.trimIndent()
        )
        dir.resolve("talaria.css").writeText("body { font-family: serif; }")
        dir.resolve("epub.css").writeText("body { margin: 0; }")
        dir.resolve("logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
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

    fun createGradleProjectWithAsciiDocSourceAndConvertedHtml(): File {
        val dir = Files.createTempDirectory("doc-bdd-n3").toFile()
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

            Contenu pour le test N3 retrieve.
            """.trimIndent()
        )
        projectDir = dir
        executeGradle("convertDocumentToHtml")
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

    fun metadataJsonFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/metadata.json")
    }

    fun compositeContextJsonFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/composite-context.json")
    }

    fun generatedPomFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/publications/pluginMaven/pom-default.xml")
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

    fun createGradleProjectWithOcrPages(photos: Boolean = false, formats: Boolean = false): File {
        val dir = Files.createTempDirectory("doc-bdd-book").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"${dir.name}\"\n"
        )
        val dsl = buildString {
            appendLine("plugins { id(\"education.cccp.document\") }")
            appendLine()
            appendLine("document {")
            appendLine("    bookPagesDir.set(file(\"pages\"))")
            if (photos) appendLine("    bookPhotosDir.set(file(\"photos\"))")
            appendLine("    bookTitle.set(\"Test Book\")")
            appendLine("    bookAuthor.set(\"Test Author\")")
            if (formats) {
                appendLine("    source.set(file(\"build/docs/document/book.adoc\"))")
            }
            appendLine("}")
        }
        dir.resolve("build.gradle.kts").writeText(dsl)

        val pagesDir = dir.resolve("pages").apply { mkdirs() }
        pagesDir.resolve("001-page.adoc").writeText("== Chapter 1\n\nFirst page content.")
        pagesDir.resolve("002-page.adoc").writeText("== Chapter 2\n\nSecond page content.")

        if (photos) {
            val photosDir = dir.resolve("photos").apply { mkdirs() }
            photosDir.resolve("001-page.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            photosDir.resolve("002-page.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }

        projectDir = dir
        return dir
    }

    fun assembledBookFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/book.adoc")
    }

    // --- DOC-12 unified DSL helpers ---

    fun createGradleProjectWithUnifiedEnrichBlock(): File {
        val dir = Files.createTempDirectory("doc-bdd-enrich").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                enrich {
                    plantuml.set(true)
                    images.set(true)
                    passthrough.set(true)
                }
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText("= Document Enrich\n\nContenu.\n")
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithUnifiedOutputsBlock(): File {
        val dir = Files.createTempDirectory("doc-bdd-out").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                outputs {
                    html.set(true)
                    pdf.set(true)
                    epub.set(true)
                }
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText("= Document Outputs\n\nContenu.\n")
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithUnifiedMetadataBlock(): File {
        val dir = Files.createTempDirectory("doc-bdd-meta").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                metadata {
                    title.set("Mon Livre")
                    author.set("Auteur")
                    language.set("fr")
                }
            }
            """.trimIndent()
        )
        dir.resolve("source.adoc").writeText("= Document Metadata\n\nContenu.\n")
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithFullUnifiedDslBlock(): File {
        val dir = Files.createTempDirectory("doc-bdd-full").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("livre.adoc"))
                enrich {
                    plantuml.set(true)
                    images.set(true)
                    passthrough.set(true)
                }
                outputs {
                    html.set(true)
                    pdf.set(true)
                    epub.set(true)
                }
                theme {
                    pdfTheme.set(file("theme.yml"))
                    htmlStylesheet.set(file("style.css"))
                    epubStylesheet.set(file("epub.css"))
                    logo.set(file("logo.png"))
                }
                metadata {
                    title.set("Mon Livre")
                    author.set("Auteur")
                    language.set("fr")
                }
            }
            """.trimIndent()
        )
        dir.resolve("livre.adoc").writeText("= Mon Livre\n\nContenu.\n")
        dir.resolve("theme.yml").writeText("extends: default\npage:\n  size: A4\n")
        dir.resolve("style.css").writeText("body { font-family: serif; }")
        dir.resolve("epub.css").writeText("body { margin: 0; }")
        dir.resolve("logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        projectDir = dir
        return dir
    }

    fun documentConfigJsonFile(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/docs/document/document-config.json")
    }

    fun createGradleProjectWithGitRepo(): File {
        val dir = Files.createTempDirectory("doc-bdd-rn").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
        runGit(dir, "init")
        runGit(dir, "config", "user.email", "bdd@example.com")
        runGit(dir, "config", "user.name", "BDD")
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithGitRepoAndMarkdownRenderer(): File {
        val dir = Files.createTempDirectory("doc-bdd-rn-md").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("markdown")
                }
            }
            """.trimIndent()
        )
        runGit(dir, "init")
        runGit(dir, "config", "user.email", "bdd@example.com")
        runGit(dir, "config", "user.name", "BDD")
        projectDir = dir
        return dir
    }

    fun createGradleProjectWithGitRepoAndCustomCategories(): File {
        val dir = Files.createTempDirectory("doc-bdd-rn-cat").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.ccp.document")
            }
            document {
                releaseNotes {
                    categories.set(mapOf(
                        "feat" to "New features",
                        "fix" to "Bug fixes",
                        "chore" to "Custom chores label"
                    ))
                }
            }
            """.trimIndent().replace("education.ccp.document", "education.cccp.document")
        )
        runGit(dir, "init")
        runGit(dir, "config", "user.email", "bdd@example.com")
        runGit(dir, "config", "user.name", "BDD")
        projectDir = dir
        return dir
    }

    fun gitCommit(message: String) {
        val dir = projectDir ?: return
        dir.resolve("README.md").appendText("# $message\n")
        runGit(dir, "add", ".")
        val pb = ProcessBuilder("git", "commit", "-m", message)
            .directory(dir)
            .redirectErrorStream(true)
        pb.environment()["GIT_AUTHOR_DATE"] = "2026-07-05T10:00:00"
        pb.environment()["GIT_COMMITTER_DATE"] = "2026-07-05T10:00:00"
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    fun gitTag(name: String) {
        val dir = projectDir ?: return
        val p = ProcessBuilder("git", "tag", name)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    fun releaseNotesOutputDir(): File? {
        val dir = projectDir ?: return null
        return dir.resolve("build/release-notes")
    }

    fun releaseNotesAdocFile(): File? {
        val dir = releaseNotesOutputDir() ?: return null
        return dir.listFiles { _, name -> name.endsWith(".adoc") }?.firstOrNull()
    }

    fun releaseNotesMarkdownFile(): File? {
        val dir = releaseNotesOutputDir() ?: return null
        return dir.listFiles { _, name -> name.endsWith(".md") }?.firstOrNull()
    }

    private fun runGit(dir: File, vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    fun cleanup() {
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
    }
}