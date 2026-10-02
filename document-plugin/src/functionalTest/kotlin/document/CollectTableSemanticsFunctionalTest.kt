package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-SEMANTIC-TABLE
 * dedicated task [CollectTableSemanticsTask] : `table-semantics.json` export +
 * severity (STRICT fails, LENIENT warns, OFF skips) + opt-in annotation.
 */
class CollectTableSemanticsFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(semanticBlock: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-table-semantics\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.semantic.TableSemanticsMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                semantic {
                    $semanticBlock
                }
            }
            """.trimIndent(),
        )
    }

    private val planningSchema = """
        schema("planning") {
            column(0, role = "reference")
            column("Title", role = "title")
            column(2, role = "objective", required = true)
        }
    """.trimIndent()

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .build()

    private fun runAndFail(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .buildAndFail()

    @Test
    fun `LENIENT extracts an annotated table into table-semantics json`() {
        writeBuild("tableSemantics.set(TableSemanticsMode.LENIENT)\n$planningSchema")
        projectDir.resolve("doc.adoc").writeText(
            """
            [.semantic-planning,options="header"]
            |===
            | Ref | Titre | Objectif

            | 1.0 | Introduction | Situer le cadre
            |===
            """.trimIndent(),
        )

        run("collectTableSemantics")

        val report = projectDir.resolve("build/docs/document/table-semantics.json")
        assertTrue(report.exists(), "the report must be written in LENIENT")
        val json = report.readText()
        assertTrue(json.contains("\"planning\""), "the table name must appear")
        assertTrue(json.contains("\"reference\"") && json.contains("\"1.0\""), "the projected record must appear")
    }

    @Test
    fun `a non-annotated table is ignored`() {
        writeBuild("tableSemantics.set(TableSemanticsMode.LENIENT)\n$planningSchema")
        projectDir.resolve("doc.adoc").writeText(
            """
            |===
            | A | B | C

            | 1 | 2 | 3
            |===
            """.trimIndent(),
        )

        run("collectTableSemantics")

        val json = projectDir.resolve("build/docs/document/table-semantics.json").readText()
        assertTrue(!json.contains("\"planning\"") || json.contains("\"tables\" : [ ]"), "an un-annotated table must never be lifted: $json")
    }

    @Test
    fun `STRICT writes the report then fails on a blank required role`() {
        writeBuild("tableSemantics.set(TableSemanticsMode.STRICT)\n$planningSchema")
        projectDir.resolve("doc.adoc").writeText(
            """
            [.semantic-planning,options="header"]
            |===
            | Ref | Titre | Objectif

            | 1.0 | Introduction |
            |===
            """.trimIndent(),
        )

        val result = runAndFail("collectTableSemantics")

        val report = projectDir.resolve("build/docs/document/table-semantics.json")
        assertTrue(report.exists(), "the report must be written even in STRICT")
        assertTrue(report.readText().contains("\"objective\""), "the finding must list the required role")
        assertTrue(result.output.contains("table semantics validation failed (STRICT)"), "STRICT must fail the build")
    }

    @Test
    fun `OFF skips lifting without writing a report`() {
        writeBuild("")
        projectDir.resolve("doc.adoc").writeText(
            """
            [.semantic-planning,options="header"]
            |===
            | Ref | Titre | Objectif

            | 1.0 | Introduction | Situer
            |===
            """.trimIndent(),
        )

        run("collectTableSemantics")

        val report = projectDir.resolve("build/docs/document/table-semantics.json")
        assertTrue(!report.exists(), "OFF must not produce a report")
    }
}
