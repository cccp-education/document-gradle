package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/**
 * Dogfooding functional test (Rule 7, read-only) — semantic lifting of a *real*
 * private FPA table (EPIC DOC-SEMANTIC-TABLE, US-6).
 *
 * The test copies a real scanned page (Ebbinghaus forgetting curve, table
 * `Durée | Perte`) into a throw-away TestKit project, opts the first table in
 * with the `[.semantic-...]` annotation (on the copy only), declares a schema
 * matching the real headers and asserts the real records are exported. It fails
 * if the *original* private source is mutated (SHA-256 before/after), and it
 * self-skips (`assumeTrue`) when the corpus is absent (isolated CI checkout).
 */
class TableSemanticContentFunctionalTest {

    companion object {
        private val CONTENT_PAGE = File(
            "/home/cheroliv/workspace/office/metiers/FPA/" +
                "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans/110.adoc",
        )

        private fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `collectTableSemantics lifts a real private table without mutating the source`() {
        assumeTrue(CONTENT_PAGE.isFile) { "content page not found at ${CONTENT_PAGE.absolutePath}" }

        val hashBefore = sha256(CONTENT_PAGE)

        // Rule 7 — never mutate the private source: work on a copy in the
        // throw-away project and opt the first real table in with the annotation.
        val annotated = CONTENT_PAGE.readText()
            .replaceFirst("|===", "[.semantic-perte,options=\"header\"]\n|===")

        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-table-semantic-content\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.semantic.TableSemanticsMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                semantic {
                    tableSemantics.set(TableSemanticsMode.LENIENT)
                    schema("perte") {
                        column("Durée", role = "duration")
                        column("Perte", role = "loss")
                    }
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(annotated)

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectTableSemantics")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val json = projectDir.resolve("build/docs/document/table-semantics.json").readText()
        assertTrue(json.contains("\"perte\""), "the real table must be lifted")
        assertTrue(json.contains("\"duration\"") && json.contains("\"20 min\""), "the real duration record must appear")
        assertTrue(json.contains("\"loss\"") && json.contains("\"42%\""), "the real loss record must appear")

        assertEquals(hashBefore, sha256(CONTENT_PAGE), "the private source must never be mutated (Rule 7)")
    }
}
