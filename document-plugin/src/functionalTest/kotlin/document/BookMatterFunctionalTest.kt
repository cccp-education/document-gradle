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
 * Functional tests (TestKit, real Gradle run) for the derived matter policy
 * (EPIC DOC-BOOK-MATTER, fixes code-review S-258 B3).
 *
 * Proves the user-visible outcome: a real table of contents that carries no
 * explicit matter (`0.x` front / `9.x` back) is *no longer* permanently flagged
 * by rule S3 — in STRICT mode the build passes, because the matter policy is
 * derived from the TOC itself. The historical behaviour stays available through
 * the `LEGACY` mode (opt-in), and the matter separators are opt-in.
 */
class BookMatterFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeProject(matterLine: String = "") {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-matter\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.MatterPolicyMode

            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Matter Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    $matterLine
                }
            }
            """.trimIndent(),
        )
    }

    /** A body-only TOC (roots 1..2), like the real scanned-content corpus. */
    private fun writeBodyOnlyPagesAndToc() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("== Part I\n\nPart content.")
        File(pagesDir, "002-chapter.adoc").writeText("=== Chapter 1\n\nChapter content.")
        File(pagesDir, "003-part2.adoc").writeText("== Part II\n\nSecond part content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1 | Chapter 1 | 2 | 002-chapter.adoc
            | 2 | Part II | 3 | 003-part2.adoc
            """.trimIndent(),
        )
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()

    private fun runExpectingFailure(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .buildAndFail()

    @Test
    fun `a body-only TOC passes STRICT validation under the derived policy`() {
        writeProject()
        writeBodyOnlyPagesAndToc()

        val result = run("assembleBook", "-Pdocument.bookValidationMode=STRICT")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val content = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertTrue(content.contains("== 1. Part I"), "the book must still be assembled")
    }

    @Test
    fun `the legacy policy still fails STRICT on a body-only TOC`() {
        writeProject("matterPolicy.set(MatterPolicyMode.LEGACY)")
        writeBodyOnlyPagesAndToc()

        val result = runExpectingFailure("assembleBook", "-Pdocument.bookValidationMode=STRICT")

        assertEquals(TaskOutcome.FAILED, result.task(":assembleBook")?.outcome)
        assertTrue(
            result.output.contains("matter"),
            "the legacy policy must keep reporting the missing matter, got: ${result.output}",
        )
    }

    @Test
    fun `matter breaks are emitted between matters when enabled`() {
        writeProject("matterBreaks.set(true)")
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-front.adoc").writeText("== Preface\n\nFront content.")
        File(pagesDir, "002-part.adoc").writeText("== Part I\n\nBody content.")
        File(pagesDir, "003-back.adoc").writeText("== Glossary\n\nBack content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 0.1 | Preface | 1 | 001-front.adoc
            | 1 | Part I | 2 | 002-part.adoc
            | 9.1 | Glossary | 3 | 003-back.adoc
            """.trimIndent(),
        )

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val content = projectDir.resolve("build/docs/document/book.adoc").readText()
        val breaks = Regex("(?m)^<<<$").findAll(content).count()
        assertTrue(breaks >= 2, "a break must separate FRONT/BODY and BODY/BACK, got $breaks:\n$content")
    }

    @Test
    fun `matter breaks are off by default`() {
        writeProject()
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-front.adoc").writeText("== Preface\n\nFront content.")
        File(pagesDir, "002-part.adoc").writeText("== Part I\n\nBody content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 0.1 | Preface | 1 | 001-front.adoc
            | 1 | Part I | 2 | 002-part.adoc
            """.trimIndent(),
        )

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val content = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertFalse(
            Regex("(?m)^<<<$").containsMatchIn(content),
            "no matter break must be emitted by default, got:\n$content",
        )
    }
}
