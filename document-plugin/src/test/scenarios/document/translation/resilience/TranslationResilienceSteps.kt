package document.translation.resilience

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import document.translation.AsciiDocParser
import document.translation.AsciiDocRenderer
import document.translation.ContentTranslationService
import document.translation.JbakeNativeRenderer
import document.translation.PivotBlock
import document.translation.PivotInline
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockTranslationStatus
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.file.Files

/**
 * DOC-TRANSLATE-RESILIENCE — the BDD contract of the delta: a block whose LLM
 * call failed must be stored PENDING and re-attempted, never frozen as
 * TRANSLATED (which would make the source-language fallback permanent).
 */
class TranslationResilienceSteps {

    private val parser = AsciiDocParser()
    private val renderer = AsciiDocRenderer()

    private lateinit var tempDir: File
    private lateinit var sourceFile: File
    private lateinit var targetFile: File

    private var failPhrase: String? = null
    private var alwaysSucceed = false
    private var statuses: Map<String, BlockChecksumEntry> = emptyMap()

    private inner class FlakyTranslationService : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            if (!alwaysSucceed && request.sourceText == failPhrase) {
                TranslationResult.Failure("LLM quota exceeded")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
    }

    @Given("a resilience fixture whose LLM fails on {string}")
    fun fixtureFailsOn(phrase: String) {
        tempDir = Files.createTempDirectory("doc-resilience").toFile()
        sourceFile = tempDir.resolve("source.adoc")
        targetFile = tempDir.resolve("target.adoc")
        failPhrase = phrase
        alwaysSucceed = false
        statuses = emptyMap()
    }

    @Given("a resilience fixture whose LLM always succeeds")
    fun fixtureAlwaysSucceeds() {
        tempDir = Files.createTempDirectory("doc-resilience-ok").toFile()
        sourceFile = tempDir.resolve("source.adoc")
        targetFile = tempDir.resolve("target.adoc")
        failPhrase = null
        alwaysSucceed = true
        statuses = emptyMap()
    }

    @Given("a resilient source article with a failing paragraph {string} and a healthy paragraph {string}")
    fun resilientSourceArticle(failing: String, healthy: String) {
        failPhrase = failing
        sourceFile.writeText(
            """= Intro

== Section One

$healthy

== Section Two

$failing
"""
        )
    }

    @When("the resilient delta translation runs")
    fun deltaTranslationRuns() {
        val service = ContentTranslationService(
            FlakyTranslationService(), parser, renderer, jbakeRenderer = JbakeNativeRenderer()
        )
        statuses = service.translateSingleFileWithBlockDelta(
            sourceFile = sourceFile,
            targetFile = targetFile,
            previousBlockChecksums = statuses,
            sourceLanguage = "fr",
            targetLanguage = "en",
        )
    }

    @When("the resilient LLM has recovered")
    fun llmHasRecovered() {
        alwaysSucceed = true
    }

    @Then("the resilient block of {string} is pending")
    fun blockIsPending(phrase: String) {
        val key = indexOfBlockContaining(phrase)
        assertThat(statuses).containsKey(key)
        assertThat(statuses.getValue(key).status).isEqualTo(BlockTranslationStatus.PENDING)
    }

    @Then("the resilient block of {string} is translated")
    fun blockIsTranslated(phrase: String) {
        val key = indexOfBlockContaining(phrase)
        assertThat(statuses).containsKey(key)
        assertThat(statuses.getValue(key).status).isEqualTo(BlockTranslationStatus.TRANSLATED)
    }

    @Then("the resilient target still contains the silent fallback {string}")
    fun targetContainsFallback(fallback: String) {
        assertThat(targetFile.readText()).contains(fallback)
    }

    @Then("the resilient target contains the translation {string}")
    fun targetContainsTranslation(translation: String) {
        assertThat(targetFile.readText()).contains(translation)
    }

    private fun indexOfBlockContaining(phrase: String): String {
        val article = parser.parse(sourceFile.readText())
        article.blocks.forEachIndexed { index, block ->
            if (blockContains(block, phrase)) return index.toString()
        }
        error("No top-level block contains '$phrase'")
    }

    private fun blockContains(block: PivotBlock, phrase: String): Boolean = when (block) {
        is PivotBlock.Heading -> block.text == phrase
        is PivotBlock.Paragraph -> inlinesContain(block.inline, phrase)
        is PivotBlock.ListBlock -> block.items.any { inlinesContain(it, phrase) }
        is PivotBlock.Table ->
            (block.header + block.rows.flatten()).any { inlinesContain(it, phrase) }
        is PivotBlock.DescriptionList ->
            block.items.any { inlinesContain(it.term, phrase) || inlinesContain(it.definition, phrase) }
        is PivotBlock.Admonition -> block.blocks.any { blockContains(it, phrase) }
        is PivotBlock.Source -> false
        is PivotBlock.BlockMacro -> false
        PivotBlock.Hr -> false
    }

    private fun inlinesContain(inlines: List<PivotInline>, phrase: String): Boolean =
        inlines.any { inline ->
            when (inline) {
                is PivotInline.Text -> inline.text == phrase
                is PivotInline.Bold -> inline.text == phrase
                is PivotInline.Link -> inline.label == phrase
                is PivotInline.Code -> false
                PivotInline.LineBreak -> false
            }
        }
}
