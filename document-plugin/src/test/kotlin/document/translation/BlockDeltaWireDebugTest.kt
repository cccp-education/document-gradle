package document.translation

import document.translation.delta.BlockChecksum
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockDelta
import document.translation.delta.BlockTranslationStatus
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class BlockDeltaWireDebugTest {

    @TempDir
    lateinit var tempDir: File

    private val fake = object : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            TranslationResult.Success("${request.sourceText} [EN]")
    }

    @Test
    fun `debug block delta second run preserves unchanged blocks`() {
        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val service = ContentTranslationService(fake, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val sourceFile = tempDir.resolve("source.adoc")
        val targetFile = tempDir.resolve("target.adoc")

        sourceFile.writeText("""= Intro

== Heading One

First paragraph.

== Heading Two

Second paragraph.
""")

        val firstChecksums = service.translateSingleFileWithBlockDelta(
            sourceFile = sourceFile,
            targetFile = targetFile,
            previousBlockChecksums = emptyMap(),
            sourceLanguage = "fr",
            targetLanguage = "en"
        )

        sourceFile.writeText("""= Intro

== Heading One

First paragraph modified.

== Heading Two

Second paragraph.
""")

        service.translateSingleFileWithBlockDelta(
            sourceFile = sourceFile,
            targetFile = targetFile,
            previousBlockChecksums = firstChecksums,
            sourceLanguage = "fr",
            targetLanguage = "en"
        )

        val content = targetFile.readText()
        assertTrue(content.contains("First paragraph modified. [EN]"))
        assertTrue(content.contains("Second paragraph. [EN]"))
    }
}