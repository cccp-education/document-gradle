package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the insertion of `translateBook`
 * into the composite `bookPipeline` (EPIC DOC-BOOK-TRANSLATE, US-3).
 *
 * When a target language is configured, the converters (`convertDocumentTo*`)
 * must consume the *translated* book (`book-<lang>.adoc`) rather than the source
 * `book.adoc`, so the whole pipeline publishes the translated book. Without a
 * target language the pipeline stays byte-identical (backward compatible).
 */
class BookPipelineTranslateFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writePages() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("Part body sentence.")
        File(pagesDir, "002-chapter.adoc").writeText("Chapter body sentence.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1 | Chapter One | 2 | 002-chapter.adoc
            """.trimIndent(),
        )
    }

    private fun writeBuild(targetLine: String, outputs: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-bookpipeline-translate\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Translated Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    sourceLanguage.set("fr")
                    $targetLine
                }
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
                outputs {
                    $outputs
                }
                translation {
                    llmMode.set("fake")
                }
            }
            """.trimIndent(),
        )
        writePages()
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()

    @Test
    fun `bookPipeline publishes the translated HTML when a target language is set`() {
        writeBuild("targetLanguage.set(\"en\")", "html.set(true)")

        val result = run("bookPipeline")

        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBook")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val html = projectDir.resolve("build/docs/document/document.html").readText()
        assertTrue(html.contains("Part I [EN]"), "the HTML must carry the translated content, got:\n${html.take(600)}")
        assertTrue(html.contains("Chapter body sentence. [EN]"), "the HTML must carry translated bodies")
    }

    @Test
    fun `bookPipeline without a target language keeps the untranslated HTML`() {
        writeBuild("", "html.set(true)")

        val result = run("bookPipeline")

        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val html = projectDir.resolve("build/docs/document/document.html").readText()
        assertFalse(html.contains("[EN]"), "no translation without a target language, got:\n${html.take(600)}")
        assertTrue(html.contains("Part I"), "the untranslated content must be present")
    }
}
