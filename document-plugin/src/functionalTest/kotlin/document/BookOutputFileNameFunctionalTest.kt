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
 * Functional tests (TestKit, real Gradle run) for `assembleBook` and
 * `enrichDocument` following the live `outputFileName` knob (S-259, fixes
 * code-review S-258 B2).
 *
 * Before S-259 both tasks wrote a *hardcoded* file name (`book.adoc` /
 * `document-enriched.adoc`) while the rest of the pipeline derived its output
 * from `outputFileName` (S-235/S-236). Consequence: with
 * `-Pdocument.outputFileName=livre`, `collectDocumentRetrieve` (which scans
 * `$outputFileName.adoc`) never indexed the assembled book.
 *
 * Defaults (`book` / `document`) keep the canonical names — fully backward
 * compatible.
 */
class BookOutputFileNameFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writePages() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-introduction.adoc").writeText("== Introduction\n\nIntro content.")
        File(pagesDir, "002-conclusion.adoc").writeText("== Conclusion\n\nConclusion content.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Introduction | 1 | 001-introduction.adoc
            | 2 | Conclusion | 2 | 002-conclusion.adoc
            """.trimIndent(),
        )
    }

    private fun writeBuild() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-output-file-name\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Knob Book")
                    author.set("Knob Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
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
    fun `assembleBook writes the renamed book when the outputFileName knob is set`() {
        writeBuild()

        val result = run("assembleBook", "-Pdocument.outputFileName=livre")
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        val renamed = projectDir.resolve("build/docs/document/livre.adoc")
        assertTrue(
            renamed.exists(),
            "the knob must rename the assembled book to livre.adoc — dir: " +
                projectDir.resolve("build/docs/document").listFiles()?.joinToString { it.name },
        )
        assertTrue(renamed.readText().contains("Intro content."), "renamed book must carry the page content")
        assertFalse(
            projectDir.resolve("build/docs/document/book.adoc").exists(),
            "the hardcoded book.adoc must NOT be produced when the knob renames the output",
        )
    }

    @Test
    fun `assembleBook without the knob keeps the canonical book dot adoc name`() {
        writeBuild()

        val result = run("assembleBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)
        assertTrue(
            projectDir.resolve("build/docs/document/book.adoc").exists(),
            "without the knob the canonical book.adoc stays (backward compat)",
        )
    }

    @Test
    fun `collect indexes the renamed assembled book when the knob is set`() {
        writeBuild()

        run("assembleBook", "-Pdocument.outputFileName=livre")
        val result = run("collectDocumentRetrieve", "-Pdocument.outputFileName=livre")
        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)

        val composite = projectDir.resolve("build/docs/document/composite-context.json").readText()
        assertTrue(
            composite.contains("livre.adoc"),
            "composite-context.json must index the renamed assembled book — actual: $composite",
        )
    }

    @Test
    fun `enrichDocument writes the renamed enriched file when the outputFileName knob is set`() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-enrich-output-file-name\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText("= Doc\n\n== Section\n\nEnrich me.")

        val result = run("enrichDocument", "-Pdocument.outputFileName=custom")
        assertEquals(TaskOutcome.SUCCESS, result.task(":enrichDocument")?.outcome)

        assertTrue(
            projectDir.resolve("build/docs/document/custom-enriched.adoc").exists(),
            "the knob must rename the enriched file to custom-enriched.adoc — dir: " +
                projectDir.resolve("build/docs/document").listFiles()?.joinToString { it.name },
        )
    }
}
