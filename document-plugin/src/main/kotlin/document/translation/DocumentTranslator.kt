package document.translation

import document.translation.delta.BlockDelta
import document.translation.plantuml.PlantUmlTranslationAdapter
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.TableSyntaxValidator
import document.translation.validation.TableValidationResult
import document.translation.validation.ValidationMode
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest
import org.slf4j.LoggerFactory

class DocumentTranslator(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val plantUmlAdapter: PlantUmlTranslationAdapter? = null,
    private val tableValidationMode: ValidationMode = ValidationMode.LENIENT,
    private val plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT,
    /**
     * US-4 PLT-DIAGRAM-OWNERSHIP (option A) — bakery (the orchestrator) builds the
     * N0 [PlantUmlTranslationPort] implementation and injects it here so document
     * delegates PlantUML label translation to the plantuml borough's port. When
     * wired, the port takes precedence over the legacy private [plantUmlAdapter]
     * (backward compat: existing callers keep working unchanged).
     */
    private val plantUmlPort: PlantUmlTranslationPort? = null,
    /** Document-side syntax validation for the port path (report `DOC-PLANTUML-VALIDATE`). */
    private val plantUmlSyntaxValidator: PlantUmlSyntaxValidator = PlantUmlSyntaxValidator.create(),
) : ArticleTranslator {

    private val log = LoggerFactory.getLogger(DocumentTranslator::class.java)

    val tableValidationResults: MutableList<TableValidationResult.Invalid> = mutableListOf()
    val plantUmlValidationResults: MutableList<PlantUmlValidationResult.Invalid> = mutableListOf()

    /**
     * DOC-TRANSLATE-RESILIENCE — indices (as `String`, matching [BlockChecksum]
     * keys) of the top-level blocks whose LLM call returned a
     * [TranslationResult.Failure]. Such a block must NOT be persisted as
     * `TRANSLATED`: [ContentTranslationService] maps these indices to
     * `PENDING` so the next batch re-attempts them instead of freezing the
     * silent French fallback forever.
     */
    val translationFailures: MutableSet<String> = linkedSetOf()

    private var currentTopLevelIndex: String? = null

    fun clearTranslationFailures() {
        translationFailures.clear()
    }

    override fun translate(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        clearTranslationFailures()
        val article = parser.parse(asciidoc)
        val translated = translateArticle(article, sourceLanguage, targetLanguage)
        val outputRenderer = if (article.frontmatter.isJbakeNative) jbakeRenderer else renderer
        return outputRenderer.render(translated)
    }

    internal fun translateArticle(
        article: PivotArticle,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotArticle {
        val translatedFrontmatter = translateFrontmatter(article.frontmatter, sourceLanguage, targetLanguage)
        val translatedBlocks = translateBlocks(article.blocks, sourceLanguage, targetLanguage, article.frontmatter.title)
        return PivotArticle(translatedFrontmatter, translatedBlocks)
    }

    /**
     * Translates a raw list of pivot blocks, preserving non-translatable content
     * (source code, HR, block macros, backtick spans) and keeping the table /
     * PlantUML validation indices consistent with [translateArticle].
     *
     * EPIC DOC-BOOK-TRANSLATE — US-1 exposes this entry point for a *body
     * fragment* (a book page has no frontmatter): [document.BookTranslator]
     * parses the fragment with [AsciiDocParser.parseBody] and feeds the blocks
     * here, so the leading paragraph is never mistaken for a header.
     *
     * @param articleTitle the owning title, used only for validation messages
     */
    fun translateBlocks(
        blocks: List<PivotBlock>,
        sourceLanguage: String,
        targetLanguage: String,
        articleTitle: String = "",
    ): List<PivotBlock> {
        var tableIndex = 0
        var plantUmlIndex = 0
        return blocks.mapIndexed { index, block ->
            currentTopLevelIndex = index.toString()
            try {
                when {
                    block is PivotBlock.Table -> {
                        val result = translateBlock(block, sourceLanguage, targetLanguage, articleTitle, tableIndex)
                        tableIndex++
                        result
                    }
                    block is PivotBlock.Source && block.language == "plantuml" -> {
                        val result = translateBlock(block, sourceLanguage, targetLanguage, articleTitle, plantUmlIndex = plantUmlIndex)
                        plantUmlIndex++
                        result
                    }
                    else -> translateBlock(block, sourceLanguage, targetLanguage)
                }
            } finally {
                currentTopLevelIndex = null
            }
        }
    }

    internal fun translateArticleWithDelta(
        sourceArticle: PivotArticle,
        previousTranslated: PivotArticle,
        delta: BlockDelta,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotArticle {
        if (delta.isEmpty()) return previousTranslated
        val translatedFrontmatter = translateFrontmatter(sourceArticle.frontmatter, sourceLanguage, targetLanguage)
        val preservedIndices = delta.preservedBlocks.toSet()
        val tableIndexByOriginalIndex = mutableMapOf<Int, Int>()
        val plantUmlIndexByOriginalIndex = mutableMapOf<Int, Int>()
        var tableIndex = 0
        var plantUmlIndex = 0
        for ((idx, block) in sourceArticle.blocks.withIndex()) {
            if (block is PivotBlock.Table) {
                tableIndexByOriginalIndex[idx] = tableIndex
                tableIndex++
            }
            if (block is PivotBlock.Source && block.language == "plantuml") {
                plantUmlIndexByOriginalIndex[idx] = plantUmlIndex
                plantUmlIndex++
            }
        }
        val translatedBlocks = sourceArticle.blocks.mapIndexed { idx, block ->
            if (idx.toString() in preservedIndices) {
                previousTranslated.blocks.getOrNull(idx) ?: translateBlock(block, sourceLanguage, targetLanguage)
            } else {
                currentTopLevelIndex = idx.toString()
                try {
                    when {
                        block is PivotBlock.Table -> {
                            val ti = tableIndexByOriginalIndex[idx] ?: 0
                            translateBlock(block, sourceLanguage, targetLanguage, sourceArticle.frontmatter.title, ti)
                        }
                        block is PivotBlock.Source && block.language == "plantuml" -> {
                            val pi = plantUmlIndexByOriginalIndex[idx] ?: 0
                            translateBlock(block, sourceLanguage, targetLanguage, sourceArticle.frontmatter.title, plantUmlIndex = pi)
                        }
                        else -> translateBlock(block, sourceLanguage, targetLanguage)
                    }
                } finally {
                    currentTopLevelIndex = null
                }
            }
        }
        return PivotArticle(translatedFrontmatter, translatedBlocks)
    }

    internal fun translateFrontmatter(
        fm: PivotFrontmatter,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotFrontmatter {
        val translatedTitle = doTranslate(fm.title, sourceLanguage, targetLanguage)
        val translatedJbakeAttrs = fm.jbakeAttributes.toMutableMap()
        translatedJbakeAttrs["summary"]?.let {
            translatedJbakeAttrs["summary"] = doTranslate(it, sourceLanguage, targetLanguage)
        }
        translatedJbakeAttrs["description"]?.let {
            translatedJbakeAttrs["description"] = doTranslate(it, sourceLanguage, targetLanguage)
        }
        val translatedAsciidocAttrs = fm.asciidocAttributes.toMutableMap()
        translatedAsciidocAttrs["summary"]?.let {
            translatedAsciidocAttrs["summary"] = doTranslate(it, sourceLanguage, targetLanguage)
        }
        translatedAsciidocAttrs["description"]?.let {
            translatedAsciidocAttrs["description"] = doTranslate(it, sourceLanguage, targetLanguage)
        }
        return fm.copy(
            title = translatedTitle,
            jbakeAttributes = translatedJbakeAttrs,
            asciidocAttributes = translatedAsciidocAttrs,
        )
    }

    private fun translateBlock(
        block: PivotBlock,
        sourceLanguage: String,
        targetLanguage: String,
        articleTitle: String = "",
        tableIndex: Int = 0,
        plantUmlIndex: Int = 0,
    ): PivotBlock = when (block) {
        is PivotBlock.Heading -> {
            val translated = doTranslate(block.text, sourceLanguage, targetLanguage)
            block.copy(text = translated)
        }
        is PivotBlock.Paragraph -> {
            block.copy(inline = translateInlines(block.inline, sourceLanguage, targetLanguage))
        }
        is PivotBlock.ListBlock -> {
            block.copy(
                items = block.items.map { items ->
                    translateInlines(items, sourceLanguage, targetLanguage)
                }
            )
        }
        is PivotBlock.Table -> {
            val translated = block.copy(
                header = block.header.map { cells ->
                    translateInlines(cells, sourceLanguage, targetLanguage)
                },
                rows = block.rows.map { row ->
                    row.map { cells ->
                        translateInlines(cells, sourceLanguage, targetLanguage)
                    }
                }
            )
            validateTranslatedTable(translated, articleTitle, tableIndex)
            translated
        }
        is PivotBlock.Admonition -> {
            block.copy(
                blocks = block.blocks.map { translateBlock(it, sourceLanguage, targetLanguage) }
            )
        }
        is PivotBlock.Source -> {
            if (block.language == "plantuml" && plantUmlPort != null) {
                translatePlantUmlWithPort(block, sourceLanguage, targetLanguage, articleTitle, plantUmlIndex)
            } else if (block.language == "plantuml" && plantUmlAdapter != null) {
                val result = plantUmlAdapter.translate(block, sourceLanguage, targetLanguage, articleTitle, plantUmlIndex)
                plantUmlValidationResults.addAll(plantUmlAdapter.plantUmlValidationResults)
                plantUmlAdapter.plantUmlValidationResults.clear()
                result
            } else {
                block
            }
        }
        is PivotBlock.DescriptionList -> {
            block.copy(
                items = block.items.map { item ->
                    DescriptionItem(
                        term = translateInlines(item.term, sourceLanguage, targetLanguage),
                        definition = translateInlines(item.definition, sourceLanguage, targetLanguage)
                    )
                }
            )
        }
        is PivotBlock.BlockMacro -> block
        is PivotBlock.Hr -> block
    }

    private fun translateInlines(
        inlines: List<PivotInline>,
        sourceLanguage: String,
        targetLanguage: String
    ): List<PivotInline> = inlines.map { translateInline(it, sourceLanguage, targetLanguage) }

    private fun translateInline(
        inline: PivotInline,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotInline = when (inline) {
        is PivotInline.Text -> {
            if (inline.translatable) {
                inline.copy(text = doTranslate(inline.text, sourceLanguage, targetLanguage))
            } else inline
        }
        is PivotInline.Bold -> {
            if (inline.translatable) {
                inline.copy(text = doTranslate(inline.text, sourceLanguage, targetLanguage))
            } else inline
        }
        is PivotInline.Code -> inline
        is PivotInline.Link -> {
            if (inline.translatable) {
                inline.copy(label = doTranslate(inline.label, sourceLanguage, targetLanguage))
            } else inline
        }
        is PivotInline.LineBreak -> inline
    }

    /**
     * US-4 PLT-DIAGRAM-OWNERSHIP (option A) — delegates a `[plantuml]` block to the
     * N0 [PlantUmlTranslationPort] (implemented by the plantuml borough, injected by
     * bakery). The pivot block is projected to a N0 [PlantUmlBlock]; a
     * [PlantUmlTranslationOutcome.Preserved] keeps the source block verbatim.
     * Validation stays document-side (the `DOC-PLANTUML-VALIDATE` report must not regress).
     */
    private fun translatePlantUmlWithPort(
        block: PivotBlock.Source,
        sourceLanguage: String,
        targetLanguage: String,
        articleTitle: String,
        blockIndex: Int,
    ): PivotBlock.Source {
        val request =
            PlantUmlTranslationRequest(
                block = PlantUmlBlock(raw = block.content),
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
            )
        val outcome = plantUmlPort!!.translate(request)
        val translated =
            when (outcome) {
                is PlantUmlTranslationOutcome.Translated -> block.copy(content = outcome.block.raw)
                is PlantUmlTranslationOutcome.Preserved -> block
            }
        validateTranslatedPlantUml(translated.content, articleTitle, blockIndex, strategy = "n0-port")
        return translated
    }

    private fun validateTranslatedPlantUml(
        plantumlCode: String,
        articleTitle: String,
        blockIndex: Int,
        strategy: String,
    ) {
        if (plantUmlValidationMode == ValidationMode.OFF) return
        val result = plantUmlSyntaxValidator.validate(plantumlCode, articleTitle, blockIndex, strategy)
        if (result is PlantUmlValidationResult.Invalid) {
            plantUmlValidationResults.add(result)
            val msg = "PlantUML validation failed in article '$articleTitle' block #$blockIndex (strategy=$strategy): ${result.reason}"
            when (plantUmlValidationMode) {
                ValidationMode.STRICT -> throw TranslationException(msg)
                ValidationMode.LENIENT -> log.warn(msg)
                ValidationMode.OFF -> {}
            }
        }
    }

    private fun validateTranslatedTable(
        table: PivotBlock.Table,
        articleTitle: String,
        tableIndex: Int,
    ) {
        if (tableValidationMode == ValidationMode.OFF) return
        val dddTable = toDddTable(table)
        val result = TableSyntaxValidator.validate(dddTable, articleTitle, tableIndex)
        if (result is TableValidationResult.Invalid) {
            tableValidationResults.add(result)
            val msg = "Table validation failed in article '$articleTitle' table #$tableIndex: ${result.reason}"
            when (tableValidationMode) {
                ValidationMode.STRICT -> throw TranslationException(msg)
                ValidationMode.LENIENT -> log.warn(msg)
                ValidationMode.OFF -> {}
            }
        }
    }

    private fun toDddTable(table: PivotBlock.Table): Table {
        val colSpecs = parser.parseColSpecs(table.cols)
        val colCount = colSpecs.size
        val allHeaderCells = table.header

        val headerRows: List<Row>
        val bodyRows: List<Row>

        if (colCount > 0 && allHeaderCells.isNotEmpty()) {
            val chunked = allHeaderCells.chunked(colCount).map { cells -> Row(cells.map { Cell(it) }) }
            headerRows = listOf(chunked.first())
            bodyRows = chunked.drop(1) + table.rows.map { row -> Row(row.map { cells -> Cell(cells) }) }
        } else {
            headerRows = if (allHeaderCells.isNotEmpty()) listOf(Row(allHeaderCells.map { Cell(it) })) else emptyList()
            bodyRows = table.rows.map { row -> Row(row.map { cells -> Cell(cells) }) }
        }
        return Table(colSpecs, headerRows, bodyRows)
    }

    private fun doTranslate(text: String, sourceLanguage: String, targetLanguage: String): String {
        if (text.isBlank()) return text
        val request = TranslationRequest(text, sourceLanguage, targetLanguage)
        return when (val result = translationService.translate(request)) {
            is TranslationResult.Success -> result.translatedText
            is TranslationResult.Failure -> {
                // DOC-TRANSLATE-RESILIENCE — the silent French fallback is kept
                // (the rendered article stays complete), but the owning top-level
                // block is remembered so it is stored as PENDING, never TRANSLATED.
                currentTopLevelIndex?.let { translationFailures.add(it) }
                text
            }
        }
    }

    /**
     * Translates a single text field (a title, a label) through the same
     * service the block translation uses, returning the original on failure or
     * blank input.
     *
     * EPIC DOC-BOOK-TRANSLATE — US-1: [document.BookTranslator] reuses this to
     * translate node / section titles without duplicating the fallback policy.
     */
    fun translateField(text: String, sourceLanguage: String, targetLanguage: String): String =
        doTranslate(text, sourceLanguage, targetLanguage)
}
