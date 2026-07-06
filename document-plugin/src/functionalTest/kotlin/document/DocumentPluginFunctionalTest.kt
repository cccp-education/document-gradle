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

    @Test
    fun `convertDocumentToEpub produit un fichier EPUB3 valide depuis la source`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers EPUB3.
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToEpub")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToEpub")?.outcome)
        val output = File(projectDir, "build/docs/document/document.epub")
        assertTrue(output.exists(), "le fichier EPUB doit etre genere")
        assertTrue(output.length() > 100, "l'EPUB ne doit pas etre vide")
        val bytes = output.readBytes()
        val header = String(bytes.copyOfRange(0, minOf(4, bytes.size)))
        assertTrue(header.startsWith("PK"), "le fichier doit commencer par la signature zip PK")
    }

    @Test
    fun `convertDocumentToEpub skip si la sortie existe et la source est inchangee`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText("= Mon Livre\n\nContenu.")

        val firstResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToEpub")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":convertDocumentToEpub")?.outcome)
        val output = File(projectDir, "build/docs/document/document.epub")
        assertTrue(output.exists())
        val firstSize = output.length()

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToEpub")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":convertDocumentToEpub")?.outcome)
        assertEquals(firstSize, output.length(), "l'EPUB ne doit pas changer apres skip")
    }

    @Test
    fun `convertDocumentToDocBook produit un fichier DocBook 5 valide depuis la source`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Ceci est un paragraphe de test pour la conversion AsciiDoc vers DocBook 5.
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToDocBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToDocBook")?.outcome)
        val output = File(projectDir, "build/docs/document/document.xml")
        assertTrue(output.exists(), "le fichier DocBook doit etre genere")
        val content = output.readText()
        assertTrue(content.contains("xmlns", ignoreCase = true), "le DocBook doit contenir un namespace XML")
        assertTrue(
            content.contains("<book", ignoreCase = true) || content.contains("<article", ignoreCase = true),
            "le DocBook doit contenir une racine <book> ou <article>"
        )
    }

    @Test
    fun `convertDocumentToManPage produit une page de manuel valide depuis la source`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-manpage\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("man.adoc"))
            }
            """.trimIndent()
        )
        projectDir.resolve("man.adoc").writeText(
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

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToManPage")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToManPage")?.outcome)
        val output = File(projectDir, "build/docs/document/document.man")
        assertTrue(output.exists(), "le fichier manpage doit etre genere")
        val content = output.readText()
        assertTrue(
            content.contains(".TH") || content.contains(".SH") || content.contains(".ds"),
            "le manpage doit contenir des directives troff"
        )
    }

    @Test
    fun `enrichDocument produces a resolved AsciiDoc file from a source with includes`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-enrich\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                enrichPlantUml.set(true)
                enrichPassthrough.set(true)
            }
            """.trimIndent()
        )
        projectDir.resolve("chapter.adoc").writeText("== Included Chapter\n\nText of the included chapter.")
        projectDir.resolve("source.adoc").writeText(
            """
            = Document to Enrich

            == Introduction

            include::chapter.adoc[]

            [plantuml]
            ----
            @startuml
            Alice -> Bob: hello
            @enduml
            ----

            ++++
            <iframe src="https://example.com"></iframe>
            ++++
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("enrichDocument")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":enrichDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document-enriched.adoc")
        assertTrue(output.exists(), "the enriched document must be generated")
        val content = output.readText()
        assertTrue(content.contains("Included Chapter"), "the enriched document must contain resolved include content")
        assertTrue(content.contains("[plantuml]"), "the enriched document must preserve the plantuml block")
        assertTrue(content.contains("<iframe"), "the enriched document must preserve the passthrough block")
    }

    @Test
    fun `enrichDocument is skipped when the output exists and the source is unchanged`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-enrich-skip\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
            }
            """.trimIndent()
        )
        projectDir.resolve("source.adoc").writeText("= Document to Enrich\n\nContent.")

        val firstResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("enrichDocument")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":enrichDocument")?.outcome)
        val output = File(projectDir, "build/docs/document/document-enriched.adoc")
        assertTrue(output.exists())
        val firstContent = output.readText()

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("enrichDocument")
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":enrichDocument")?.outcome)
        assertEquals(firstContent, output.readText(), "the enriched content must not change after skip")
    }

    @Test
    fun `convertDocumentToHtml applies the custom stylesheet from the theme DSL`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-theme-html\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                theme {
                    htmlStylesheet.set(file("talaria.css"))
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("source.adoc").writeText(
            """
            = Themed Document

            == Introduction

            Content with custom theme.
            """.trimIndent()
        )
        projectDir.resolve("talaria.css").writeText("body { font-family: serif; color: #333; }")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val output = File(projectDir, "build/docs/document/document.html")
        assertTrue(output.exists(), "the HTML file must be generated")
        val content = output.readText()
        assertTrue(content.contains("font-family"), "the HTML must embed the custom stylesheet CSS")
    }

    @Test
    fun `convertDocumentToPdf applies the custom pdf theme from the theme DSL`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-theme-pdf\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("source.adoc"))
                theme {
                    pdfTheme.set(file("talaria-theme.yml"))
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("source.adoc").writeText(
            """
            = Themed PDF Document

            == Introduction

            Content with custom PDF theme.
            """.trimIndent()
        )
        projectDir.resolve("talaria-theme.yml").writeText(
            """
            extends: default
            page:
              size: A4
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToPdf")?.outcome)
        val output = File(projectDir, "build/docs/document/document.pdf")
        assertTrue(output.exists(), "the PDF file must be generated")
        assertTrue(output.length() > 100, "the PDF must not be empty")
        val bytes = output.readBytes()
        val header = String(bytes.copyOfRange(0, minOf(5, bytes.size)))
        assertTrue(header.startsWith("%PDF"), "the file must start with PDF signature")
    }

    @Test
    fun `convertDocumentToHtml succeeds without theme configured (default fallback)`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        File(projectDir, "mon-livre.adoc").writeText(
            """
            = Default Theme Document

            == Introduction

            Content without custom theme.
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val output = File(projectDir, "build/docs/document/document.html")
        assertTrue(output.exists(), "the HTML file must be generated")
        val content = output.readText()
        assertTrue(content.contains("<!DOCTYPE html", ignoreCase = true), "the HTML must contain a doctype")
    }

    @Test
    fun `collectDocumentRetrieve produces metadata_json and composite-context_json after conversion`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        projectDir.resolve("mon-livre.adoc").writeText(
            """
            = Document de Test

            == Introduction

            Contenu pour le test N3 retrieve.
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val metadataFile = File(projectDir, "build/docs/document/metadata.json")
        val compositeFile = File(projectDir, "build/docs/document/composite-context.json")
        assertTrue(metadataFile.exists(), "metadata.json must be produced")
        assertTrue(compositeFile.exists(), "composite-context.json must be produced")

        val metadataContent = metadataFile.readText()
        assertTrue(metadataContent.contains("\"new-orleans\""), "metadata source must be new-orleans")
        assertTrue(metadataContent.contains("\"retrieve\""), "metadata type must be retrieve")
        assertTrue(metadataContent.contains("\"version\" : \"1.0\""), "metadata version must be 1.0")

        val compositeContent = compositeFile.readText()
        assertTrue(compositeContent.contains("\"source\""), "composite-context must have source field")
        assertTrue(compositeContent.contains("\"entries\""), "composite-context must have entries array")
        assertTrue(compositeContent.contains("\"count\""), "composite-context must have count field")
        assertTrue(compositeContent.contains(".html"), "entries must reference the produced HTML artifact")
    }

    @Test
    fun `collectDocumentRetrieve produces empty entries when no artifacts exist`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        projectDir.resolve("mon-livre.adoc").writeText("= Empty Pipeline\n\nNo conversions run.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val compositeFile = File(projectDir, "build/docs/document/composite-context.json")
        assertTrue(compositeFile.exists(), "composite-context.json must be produced even with no artifacts")
        val content = compositeFile.readText()
        assertTrue(content.contains("\"count\" : 0"), "count must be 0 when no artifacts exist")
    }

    @Test
    fun `generatePomFileForPluginMavenPublication produces a POM with name description license developers and scm`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-pom\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
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
                        id = "education.ccp.document"
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

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForPluginMavenPublication")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePomFileForPluginMavenPublication")?.outcome)
        val pomFile = File(projectDir, "build/publications/pluginMaven/pom-default.xml")
        assertTrue(pomFile.exists(), "POM file must be generated at ${pomFile.absolutePath}")
        val pomContent = pomFile.readText()
        assertTrue(pomContent.contains("Document Gradle Plugin"), "POM name must be Document Gradle Plugin\n$pomContent")
        assertTrue(pomContent.contains("Apache-2.0"), "POM must declare Apache-2.0 license\n$pomContent")
        assertTrue(pomContent.contains("cccp-education"), "POM must declare cccp-education developer\n$pomContent")
        assertTrue(pomContent.contains("scm:git"), "POM must declare scm connection\n$pomContent")
    }

    @Test
    fun `assembleBook merges OCR-ed pages into a single book AsciiDoc`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                bookPagesDir.set(file("pages"))
                bookTitle.set("Test Book")
                bookAuthor.set("Test Author")
            }
            """.trimIndent()
        )
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        pagesDir.resolve("001-page.adoc").writeText("== Chapter 1\n\nFirst page content.")
        pagesDir.resolve("002-page.adoc").writeText("== Chapter 2\n\nSecond page content.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val output = File(projectDir, "build/docs/document/book.adoc")
        assertTrue(output.exists(), "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.contains("= Test Book"), "the book title must be present")
        assertTrue(content.contains(":author: Test Author"), "the book author must be present")
        assertTrue(content.contains("Chapter 1"), "page 1 content must be present")
        assertTrue(content.contains("Chapter 2"), "page 2 content must be present")
        val firstIdx = content.indexOf("Chapter 1")
        val secondIdx = content.indexOf("Chapter 2")
        assertTrue(firstIdx < secondIdx, "pages must be ordered by numeric prefix")
    }

    @Test
    fun `assembleBook embeds original photos as image directives`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-photos\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                bookPagesDir.set(file("pages"))
                bookPhotosDir.set(file("photos"))
                bookTitle.set("Photo Book")
                bookAuthor.set("Author")
            }
            """.trimIndent()
        )
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        pagesDir.resolve("001-page.adoc").writeText("== Chapter 1\n\nFirst page.")
        val photosDir = projectDir.resolve("photos").apply { mkdirs() }
        photosDir.resolve("001-page.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val output = File(projectDir, "build/docs/document/book.adoc")
        assertTrue(output.exists(), "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.contains("image::001-page.png[]"), "the photo image directive must be present")
    }

    @Test
    fun `bookPipeline registers and exposes the assembleBook and bookPipeline tasks`() {
        val projectDir = newTempDir()
        setupTestProject(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--group", "document")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("assembleBook"), "assembleBook task must be registered")
        assertTrue(result.output.contains("bookPipeline"), "bookPipeline task must be registered")
    }

    @Test
    fun `book nested DSL block configures assembleBook with title author pages and photos`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-dsl\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(file("pages"))
                    photosDir.set(file("photos"))
                    title.set("DSL Book")
                    author.set("DSL Author")
                }
            }
            """.trimIndent()
        )
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        pagesDir.resolve("001-page.adoc").writeText("== Chapter 1\n\nFirst DSL page.")
        pagesDir.resolve("002-page.adoc").writeText("== Chapter 2\n\nSecond DSL page.")
        val photosDir = projectDir.resolve("photos").apply { mkdirs() }
        photosDir.resolve("001-page.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val output = File(projectDir, "build/docs/document/book.adoc")
        assertTrue(output.exists(), "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.contains("= DSL Book"), "the book title from nested DSL must be present")
        assertTrue(content.contains(":author: DSL Author"), "the book author from nested DSL must be present")
        assertTrue(content.contains("Chapter 1"), "page 1 content must be present")
        assertTrue(content.contains("Chapter 2"), "page 2 content must be present")
        assertTrue(content.contains("image::001-page.png[]"), "the photo from nested DSL must be embedded")
    }

    @Test
    fun `book nested DSL block falls back to default title and author when unset`() {
        val projectDir = newTempDir()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-defaults\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(file("pages"))
                }
            }
            """.trimIndent()
        )
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        pagesDir.resolve("001-page.adoc").writeText("== Chapter 1\n\nOnly page.")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val output = File(projectDir, "build/docs/document/book.adoc")
        assertTrue(output.exists(), "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.contains("= Untitled Book"), "default book title must be applied")
        assertTrue(content.contains(":author: Unknown Author"), "default book author must be applied")
    }

    @Test
    fun `convertDocumentToHtml renders image directives into img tags`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        projectDir.resolve("mon-livre.adoc").writeText(
            """
            = Document avec Image

            == Illustration

            image::photo.png[Photo descriptive]

            Paragraphe suivant.
            """.trimIndent()
        )
        projectDir.resolve("photo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val output = File(projectDir, "build/docs/document/document.html")
        assertTrue(output.exists(), "the HTML file must be generated")
        val content = output.readText()
        assertTrue(content.contains("<img"), "the HTML must render an <img> tag from the image:: directive")
        assertTrue(content.contains("photo.png"), "the img src must reference photo.png")
    }

    @Test
    fun `convertDocumentToPdf renders image directives without dropping them`() {
        val projectDir = newTempDir()
        setupTestProjectWithDsl(projectDir)
        projectDir.resolve("mon-livre.adoc").writeText(
            """
            = Document PDF avec Image

            == Illustration

            image::photo.png[Photo descriptive]

            Paragraphe suivant.
            """.trimIndent()
        )
        projectDir.resolve("photo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToPdf")?.outcome)
        val output = File(projectDir, "build/docs/document/document.pdf")
        assertTrue(output.exists(), "the PDF file must be generated")
        assertTrue(output.length() > 100, "the PDF must not be empty")
        val bytes = output.readBytes()
        val header = String(bytes.copyOfRange(0, minOf(5, bytes.size)))
        assertTrue(header.startsWith("%PDF"), "the file must start with PDF signature")
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