package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

/**
 * Functional tests (TestKit, real Gradle run) for the image-aware book assembly
 * (EPIC DOC-BOOK-IMAGES, fixes code-review S-258 B4/B5).
 *
 * Proves the two user-visible outcomes:
 * - *B4* — with a photos directory, the structured assembly emits one
 *   `image::` per page scan and an `:imagesdir:` attribute, so the scanned book
 *   is illustrated and the conversion can embed the pictures;
 * - *B5* — a ghost `image::` directive (caption without file) is repaired with
 *   the page scan (or dropped), so the EPUB conversion embeds a real resource
 *   instead of referencing a missing one (RSC-007 otherwise).
 *
 * The default (no photos directory) stays exactly as before — backward
 * compatible.
 */
class BookImagesFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    /** A valid 1×1 PNG so Asciidoctor can actually embed it in the EPUB. */
    private fun writePng(file: File) {
        val bytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        )
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }

    private fun writeProject(photosConfigured: Boolean, photosSeparate: Boolean = false) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-images\"\n")
        val photosLine = if (photosConfigured) {
            if (photosSeparate) {
                "photosDir.set(layout.projectDirectory.dir(\"photos\"))"
            } else {
                "photosDir.set(layout.projectDirectory.dir(\"pages\"))"
            }
        } else {
            ""
        }
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    $photosLine
                    title.set("Illustrated Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Introduction | 1 | 001-introduction.adoc
            | 2 | Conclusion | 2 | 002-conclusion.adoc
            """.trimIndent(),
        )
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()

    @Test
    fun `assembleBook illustrates each page with its scan and emits imagesdir when photos are configured`() {
        writeProject(photosConfigured = true)
        val pages = projectDir.resolve("pages").apply { mkdirs() }
        pages.resolve("001-introduction.adoc").writeText("== Introduction\n\nIntro content.")
        pages.resolve("002-conclusion.adoc").writeText("== Conclusion\n\nConclusion content.")
        // scans live next to the pages, named by page number (scanned-corpus convention)
        writePng(pages.resolve("001.png"))
        writePng(pages.resolve("002.png"))

        val result = run("assembleBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        val book = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertTrue(":imagesdir:" in book, "the imagesdir attribute must be emitted, got:\n${book.take(400)}")
        assertTrue("image::001.png[]" in book, "page 1 scan must be referenced, got:\n$book")
        assertTrue("image::002.png[]" in book, "page 2 scan must be referenced, got:\n$book")
    }

    @Test
    fun `assembleBook without photos keeps the canonical book unchanged`() {
        writeProject(photosConfigured = false)
        val pages = projectDir.resolve("pages").apply { mkdirs() }
        pages.resolve("001-introduction.adoc").writeText("== Introduction\n\nIntro content.")
        pages.resolve("002-conclusion.adoc").writeText("== Conclusion\n\nConclusion content.")
        writePng(pages.resolve("001.png"))

        val result = run("assembleBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        val book = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertFalse(":imagesdir:" in book, "no imagesdir without photos, got:\n${book.take(400)}")
        assertFalse("image::" in book, "no illustration without photos, got:\n$book")
    }

    @Test
    fun `assembleBook repairs a ghost image with the page scan so the EPUB can embed it`() {
        writeProject(photosConfigured = false)
        val pages = projectDir.resolve("pages").apply { mkdirs() }
        pages.resolve("001-introduction.adoc").writeText(
            "== Introduction\n\nLe schema :\nimage::qr-code.png[QR Code]\nSuite du texte.",
        )
        writePng(pages.resolve("001.png"))
        pages.resolve("002-conclusion.adoc").writeText("== Conclusion\n\nConclusion content.")

        val result = run("assembleBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        val book = projectDir.resolve("build/docs/document/book.adoc").readText()
        assertTrue("image::001.png[QR Code]" in book, "the ghost must be repaired to the page scan, got:\n$book")
        assertFalse("qr-code.png" in book, "the ghost target must disappear, got:\n$book")
        assertTrue(":imagesdir:" in book, "repaired scans need the imagesdir to be embedded, got:\n${book.take(400)}")
    }

    @Test
    fun `assembleBook then convertDocumentToEpub embeds the page scan as an EPUB resource`() {
        writeProject(photosConfigured = true)
        val pages = projectDir.resolve("pages").apply { mkdirs() }
        pages.resolve("001-introduction.adoc").writeText("== Introduction\n\nIntro content.")
        pages.resolve("002-conclusion.adoc").writeText("== Conclusion\n\nConclusion content.")
        writePng(pages.resolve("001.png"))
        writePng(pages.resolve("002.png"))

        run("assembleBook")
        val result = run("convertDocumentToEpub")
        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToEpub")?.outcome)

        val epub = projectDir.resolve("build/docs/document/document.epub")
        assertTrue(epub.isFile, "the EPUB must be produced")
        val entries = ZipFile(epub).use { zf -> zf.entries().toList().map { it.name } }
        assertTrue(
            entries.any { it.contains("001.png") },
            "the EPUB must embed the page scan referenced by image:: (RSC-007 otherwise), entries: $entries",
        )
    }
}
