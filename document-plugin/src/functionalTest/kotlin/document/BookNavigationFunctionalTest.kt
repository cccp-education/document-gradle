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
 * Functional tests (TestKit, real Gradle run) for the opt-in previous / next
 * navigation links (EPIC DOC-BOOK-CONSISTENCY-B6, fixes code-review S-258 B6).
 *
 * Proves the user-visible outcome: `assembleBook` can append a previous / next
 * cross-reference at the foot of every emitted section, and the knob is off by
 * default (backward compatible).
 */
class BookNavigationFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeProject(navigationLine: String = "") {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-navigation\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Navigation Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    $navigationLine
                }
            }
            """.trimIndent(),
        )
    }

    /** A three-section body TOC (roots 1..1.2). */
    private fun writePagesAndToc() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("== Part I\n\nPart content.")
        File(pagesDir, "002-chapter.adoc").writeText("=== Chapter 1\n\nChapter content.")
        File(pagesDir, "003-chapter2.adoc").writeText("=== Chapter 2\n\nSecond chapter content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1 | Chapter 1 | 2 | 002-chapter.adoc
            | 1.2 | Chapter 2 | 3 | 003-chapter2.adoc
            """.trimIndent(),
        )
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()

    @Test
    fun `navigation links are emitted when enabled`() {
        writeProject("navigation.set(true)")
        writePagesAndToc()

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val content = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertTrue(
            content.contains("<<1,Part I>> | <<1.2,Chapter 2>>"),
            "section 1.1 must link to its previous and next, got:\n$content",
        )
    }

    @Test
    fun `navigation links are off by default`() {
        writeProject()
        writePagesAndToc()

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val content = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertFalse(content.contains("<<1,Part I>>"), "no navigation link must be emitted by default")
    }
}
