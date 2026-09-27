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
 * Functional tests (TestKit, real Gradle run) for the `publishBookAllLanguages`
 * task (EPIC DOC-BOOK-PUBLISH, US-2).
 *
 * Proves the user-visible outcome: every translated `book-<lang>.adoc` is fanned
 * out into each requested format as `book-<lang>.<ext>`, with the translated
 * content preserved. The translation is produced offline by the deterministic
 * fake LLM (`translateLlmMode=fake`) so the run is metered-free.
 */
class BookPublishFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writePages() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("Part body sentence.")
        File(pagesDir, "002-chapter.adoc").writeText("Chapter body sentence.")
        File(pagesDir, "003-chapter2.adoc").writeText("Second chapter body sentence.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1 | Chapter One | 2 | 002-chapter.adoc
            | 1.2 | Chapter Two | 3 | 003-chapter2.adoc
            """.trimIndent(),
        )
    }

    private fun writeBuild(knobs: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-publish\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Published Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    sourceLanguage.set("fr")
                    $knobs
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

    private fun docsDir(): File = projectDir.resolve("build/docs/document")

    @Test
    fun `each translated book is published in every requested format`() {
        writeBuild(
            """
            |targetLanguages.set(listOf("en", "de"))
            |publishFormats.set(listOf("html"))
            """.trimMargin(),
        )

        val result = run("publishBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishBookAllLanguages")?.outcome)

        listOf("en", "de").forEach { lang ->
            val html = docsDir().resolve("book-$lang.html")
            assertTrue(html.isFile, "book-$lang.html must be produced — dir: ${docsDir().listFiles()?.joinToString { it.name }}")
            assertTrue(html.readText().contains("[${lang.uppercase()}]"), "book-$lang.html must carry the translated content")
        }
    }

    @Test
    fun `the conversion is derived from the translated book and preserves the anchors`() {
        writeBuild(
            """
            |targetLanguages.set(listOf("en"))
            |publishFormats.set(listOf("html"))
            """.trimMargin(),
        )

        val result = run("publishBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishBookAllLanguages")?.outcome)

        // translateBookAllLanguages runs first (dependsOn) and produces the source.
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome)
        assertTrue(docsDir().resolve("book-en.adoc").isFile, "the translated source book must exist")

        val html = docsDir().resolve("book-en.html").readText()
        assertTrue(html.contains("[EN]"), "the HTML must carry the translated content")
        assertTrue(html.contains("id=\"1.1\"") || html.contains("[[1.1]]") || html.contains("1.1"), "the HTML must preserve the section reference")
    }

    @Test
    fun `no publish format leaves publishBookAllLanguages inactive`() {
        writeBuild("targetLanguages.set(listOf(\"en\"))")

        val result = run("publishBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishBookAllLanguages")?.outcome)

        assertFalse(
            docsDir().listFiles()?.any { it.name.matches(Regex("book-[a-z]+\\.html")) } ?: false,
            "no book must be published without a format knob — dir: ${docsDir().listFiles()?.joinToString { it.name }}",
        )
    }

    @Test
    fun `the CLI publish formats override the DSL knobs`() {
        writeBuild(
            """
            |targetLanguages.set(listOf("en"))
            |publishFormats.set(listOf("html"))
            """.trimMargin(),
        )

        val result = run("publishBookAllLanguages", "-Pdocument.bookPublishFormats=epub")
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishBookAllLanguages")?.outcome)

        assertTrue(docsDir().resolve("book-en.epub").isFile, "the CLI format must produce book-en.epub")
        assertFalse(docsDir().resolve("book-en.html").exists(), "the CLI must override the DSL html format")
    }

    @Test
    fun `the outputFileName knob drives both the source and the published books`() {
        writeBuild(
            """
            |targetLanguages.set(listOf("en"))
            |publishFormats.set(listOf("html"))
            """.trimMargin(),
        )

        run("publishBookAllLanguages", "-Pdocument.outputFileName=livre")

        assertTrue(docsDir().resolve("livre-en.adoc").isFile, "the translated source must follow the knob")
        assertTrue(docsDir().resolve("livre-en.html").isFile, "the published book must follow the knob")
    }
}
