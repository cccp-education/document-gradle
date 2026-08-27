package document

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

class AssembleBookTaskUnitTest {
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        // Create a unique temporary directory for this test
        tempDir = Files.createTempDirectory("assemblebooktest").toFile()
    }

    @AfterEach
    fun tearDown() {
        // Delete the temp directory recursively
        deleteRecursively(tempDir)
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        if (!file.delete()) {
            throw AssertionError("Failed to delete $file")
        }
    }

    @Test
    fun `assembleBook produces structured book from TOC`() {
        // Create the task directly
        val task = AssembleBookTask()

        // Set up directories and files
        val pagesDir = File(tempDir, "pages")
        pagesDir.mkdirs()
        val photosDir = File(tempDir, "photos")
        photosDir.mkdirs()
        val pdfsDir = File(tempDir, "pdfs")
        pdfsDir.mkdirs()
        val tocFile = File(tempDir, "toc.adoc")
        val outputFile = File(tempDir, "output.adoc")

        // Set task properties
        task.pagesDir.set(pagesDir)
        task.photosDir.set(photosDir)
        task.pdfsDir.set(pdfsDir)
        task.title.set("Test Book")
        task.author.set("Test Author")
        task.tocFile.set(tocFile)
        task.outputFileName.set("output.adoc")
        task.validationMode.set(ValidationMode.LENIENT)
        task.outputFile.set(outputFile)

        // Create the TOC file
        tocFile.writeText("""
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Introduction | 1 | 001-introduction.adoc
            | 2 | Conclusion | 2 | 002-conclusion.adoc
            """.trimIndent())

        // Create the page files
        File(pagesDir, "001-introduction.adoc").writeText("== Introduction\n\nThis is the introduction.")
        File(pagesDir, "002-conclusion.adoc").writeText("== Conclusion\n\nThis is the conclusion.")

        // Execute the task
        task.assemble()

        // Check the output file
        assertTrue(outputFile.exists(), "Output file should exist")
        val content = outputFile.readText()
        assertTrue(content.contains("= Test Book"), "Title page missing")
        assertTrue(content.contains(":author: Test Author"), "Author missing")
        assertTrue(content.contains(":toc:"), "TOC attribute missing")
        assertTrue(content.contains("== 1. Introduction"), "Introduction heading missing")
        assertTrue(content.contains("== 2. Conclusion"), "Conclusion heading missing")
        assertTrue(content.contains("This is the introduction."), "Introduction content missing")
        assertTrue(content.contains("This is the conclusion."), "Conclusion content missing")
    }
}