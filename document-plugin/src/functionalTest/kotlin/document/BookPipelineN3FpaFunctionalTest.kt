package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Dogfooding functional test — the *complete* N3 pipeline against the real
 * FPA corpus (Option A of session 233, PROMPT_REPRISE).
 *
 * Session 233 goal : lock the full N3 status of the "livre FPA SERVER" product.
 * Prior sessions proved each link in isolation :
 * - S-217/218 : `bookPipeline` produces the navigable book (HTML/PDF/EPUB) ;
 * - S-226     : `HtmlLinkLinter` validates the *real* rendered book.html (Valid) ;
 * - S-231     : `collectDocumentRetrieve` carries `validationStatus` into metadata.json ;
 * - S-232     : `validateDocument` composes the four guards (include + xref +
 *   security + htmlLint).
 *
 * This test proves the *chain* on the real FPA corpus : `bookPipeline
 * validateDocument collectDocumentRetrieve` in one build produces
 * `document-validation-report.json` with all four guards audited (VALID) and
 * `metadata.json` carrying `validationStatus=PASS` — the composite validation
 * status the runner-gradle N3 dashboard will read.
 *
 * The FPA corpus is consumed read-only (Rule 7): pages are copied into a
 * throw-away TestKit project, and the test self-skips (`assumeTrue`) when the
 * corpus is absent.
 *
 * Guard severity: xref + htmlLint are STRICT (fail-fast on unresolved cross-reference
 * or dead HTML link — the real navigability lock), while the include guard stays LENIENT
 * with `SafeMode.UNSAFE` (DOC-CR5 coherence — Warn advice, not Reject; the STRICT×SERVER
 * combination is unusable in TestKit because the AsciidoctorJ SERVER jail is the daemon's
 * working directory, not the throw-away project dir). Passing STRICT on the two
 * filesystem-free guards proves zero dead link / unresolved xref in the real book.
 */
class BookPipelineN3FpaFunctionalTest {

    companion object {
        private val FPA_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val FPA_TOC = File(FPA_DIR, "toc.adoc")
        private val FPA_SCANS = File(
            FPA_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `full N3 pipeline on the real FPA book carries validationStatus PASS in metadata`() {
        assumeTrue(FPA_TOC.isFile) { "FPA TOC not found at ${FPA_TOC.absolutePath}" }
        assumeTrue(FPA_SCANS.isDirectory) { "FPA scans not found at ${FPA_SCANS.absolutePath}" }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-bookpipeline-n3-fpa"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.security.IncludeGuardMode
            import document.validation.HtmlLinkLintMode
            import document.xref.XrefValidationMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("fpa/pages"))
                    title.set("Devenir Formateur Professionnel d'Adultes - FPA II")
                    author.set("CCCP Education")
                    tocFile.set(layout.projectDirectory.file("fpa/toc.adoc"))
                }
                // enrich/collect read this source; the assembled book lives here
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
                converter {
                    // DOC-CR5 coherence: LENIENT include guard × UNSAFE safe mode is a Warn
                    // (visibility, not failure). STRICT is not usable here: the AsciidoctorJ
                    // SERVER safe mode jails the filesystem on the TestKit daemon's working
                    // directory, rejecting the EPUB `to_dir` in the throw-away project dir.
                    // xref + htmlLint stay STRICT — no-filesystem guards, fail-fast kept for
                    // cross-references and dead HTML links (the real navigability lock).
                    includeGuard = IncludeGuardMode.LENIENT
                    xrefValidation = XrefValidationMode.STRICT
                    htmlLinkLint = HtmlLinkLintMode.STRICT
                    safeMode = SafeMode.UNSAFE
                }
            }
            """.trimIndent(),
        )

        // Copy the TOC + referenced pages (BookPipelineFpaFunctionalTest pattern).
        val pagesDir = projectDir.resolve("fpa/pages").apply { mkdirs() }
        val tocText = FPA_TOC.readText()
        FPA_TOC.copyTo(projectDir.resolve("fpa/toc.adoc"), overwrite = true)
        val referenced = tocText.lines()
            .mapNotNull { line ->
                val cells = line.trim().split("|").map { it.trim() }.drop(1)
                if (cells.size < 4) return@mapNotNull null
                val ref = cells[0]
                val fileName = cells[3]
                if (Regex("""\d+(\.\d+)*""").matches(ref) && fileName.endsWith(".adoc")) {
                    fileName
                } else {
                    null
                }
            }
        assumeTrue(referenced.isNotEmpty()) { "no .adoc page reference found in the FPA TOC" }
        referenced.forEach { name ->
            val page = File(FPA_SCANS, name)
            assumeTrue(page.isFile) { "referenced FPA page '$name' not found in scans" }
            page.copyTo(pagesDir.resolve(name), overwrite = true)
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("bookPipeline")
            .withPluginClasspath()
            .build()
        // The N3 status chain runs as two further builds: `validateDocument` audits
        // the rendered book (4 guards) and writes the composite report, then the
        // collect re-scans (snapshot contract: always executes, never up-to-date) and
        // metadata.json finally carries the fresh validationStatus. In a single build
        // the two tasks would conflict — collect.outputDir covers the book.adoc
        // validateDocument audits — and Gradle forbids circular ordering; the staged
        // chain is the contract: produce, validate, then index.
        val resultValidated = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument")
            .withPluginClasspath()
            .build()
        val resultCollect = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        // --- the full N3 chain succeeds
        for (task in listOf(
            ":assembleBook",
            ":convertDocumentToHtml",
            ":bookPipeline",
        )) {
            assertEquals(TaskOutcome.SUCCESS, result.task(task)?.outcome, "task $task must succeed")
        }
        assertEquals(TaskOutcome.SUCCESS, resultValidated.task(":validateDocument")?.outcome, "task :validateDocument must succeed")
        assertEquals(
            TaskOutcome.SUCCESS,
            resultCollect.task(":collectDocumentRetrieve")?.outcome,
            "task :collectDocumentRetrieve must re-run after the validation report changed",
        )

        // --- consolidated report audited all four guards on the real book
        // (raw-JSON asserts — the functionalTest source set does not expose
        // jackson-module-kotlin; parity with the FT metadata asserts pattern)
        val reportFile = projectDir.resolve("build/docs/document/document-validation-report.json")
        assertTrue(reportFile.isFile, "the consolidated validation report must be produced")
        val report = reportFile.readText()
        // includeGuard VALID (no traversal in the real book), xref VALID (no unresolved
        // cross-reference), security WARN (DOC-CR5: LENIENT include guard × UNSAFE safe
        // mode — visibility advice, not a failure), htmlLint VALID (navigable HTML).
        assertTrue(report.contains("\"status\" : \"INVALID\"") == false, "include guard must not fail on the real FPA book — actual: $report")
        assertTrue(report.contains("\"status\" : \"MISSING\"") == false, "xref must have no unresolved reference on the real FPA book — actual: $report")
        assertTrue(report.contains("\"status\" : \"DEAD\"") == false, "htmlLint must find no dead internal link on the real FPA book — actual: $report")
        assertTrue(report.contains("\"advice\" : \"REJECT\"") == false, "security must not reject the N3 chain config — actual: $report")
        assertTrue(report.contains("\"htmlLint\""), "the fourth guard must be audited — actual: $report")

        // --- the N3 contract proves it end-to-end: metadata.json carries the status
        val metadataContent = projectDir.resolve("build/docs/document/metadata.json").readText()
        assertTrue(
            metadataContent.contains("\"validationStatus\" : \"PASS\""),
            "metadata.json must carry validationStatus PASS for runner-gradle N3 — actual: $metadataContent",
        )
    }
}