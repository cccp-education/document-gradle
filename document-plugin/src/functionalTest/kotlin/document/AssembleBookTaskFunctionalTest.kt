package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests for the `assembleBook` task (TestKit, real Gradle run).
 *
 * DOC-BOOK-DOMAIN-6 — structural validation integration:
 * - a structurally invalid TOC (level jump `1` → `1.1.1` with no intermediate
 *   `1.1`) fails the build in STRICT mode, even when every physical page
 *   exists and is non-empty;
 * - the same TOC only logs warnings in LENIENT mode (default) and the build
 *   succeeds.
 */
class AssembleBookTaskFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupPluginProject() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-assemblebook"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Test Book")
                    author.set("Test Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeValidPagesButJumpedToc() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("== Part I\n\nPart content.")
        File(pagesDir, "002-jumped.adoc").writeText("== Jumped section\n\nJumped content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1.1 | Jumped section | 2 | 002-jumped.adoc
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
    fun `assembleBook is registered in the document task group`() {
        setupPluginProject()
        val result = run("tasks", "--group", "document")
        assertTrue(result.output.contains("assembleBook"), "assembleBook must be registered")
    }

    @Test
    fun `assembleBook produces structured book from TOC`() {
        setupPluginProject()

        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-introduction.adoc").writeText("== Introduction\n\nThis is the introduction.")
        File(pagesDir, "002-conclusion.adoc").writeText("== Conclusion\n\nThis is the conclusion.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Introduction | 1 | 001-introduction.adoc
            | 2 | Conclusion | 2 | 002-conclusion.adoc
            """.trimIndent(),
        )

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val outputFile = projectDir.resolve("build/docs/document/book.adoc")
        assertTrue(outputFile.exists(), "output file should exist")

        val content = outputFile.readText()
        assertTrue(content.contains("= Test Book"), "Title page missing")
        assertTrue(content.contains(":author: Test Author"), "Author missing")
        assertTrue(content.contains(":toc:"), "TOC attribute missing")
        assertTrue(content.contains("== 1. Introduction"), "Introduction heading missing")
        assertTrue(content.contains("== 2. Conclusion"), "Conclusion heading missing")
        assertTrue(content.contains("This is the introduction."), "Introduction content missing")
        assertTrue(content.contains("This is the conclusion."), "Conclusion content missing")
    }

    @Test
    fun `assembleBook in STRICT mode fails the build on a level jump even with all pages present`() {
        setupPluginProject()
        writeValidPagesButJumpedToc()

        val result = runExpectingFailure("assembleBook", "-Pdocument.bookValidationMode=STRICT")

        assertEquals(TaskOutcome.FAILED, result.task(":assembleBook")?.outcome)
        assertTrue(
            result.output.contains("book validation failed"),
            "the build must fail with a validation report, got: ${result.output}",
        )
        assertTrue(
            result.output.contains("1.1"),
            "the report must cite the missing parent ref 1.1, got: ${result.output}",
        )
    }

    @Test
    fun `assembleBook in LENIENT mode survives a level jump and still assembles the book`() {
        setupPluginProject()
        writeValidPagesButJumpedToc()

        val result = run("assembleBook")

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        val outputFile = projectDir.resolve("build/docs/document/book.adoc")
        assertTrue(outputFile.exists(), "the lenient mode must not prevent the assembly")
        assertTrue(
            outputFile.readText().contains("Jumped content."),
            "the jumped section content must still be assembled",
        )
    }
}
