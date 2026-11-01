package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest
import document.translation.delta.BlockDelta
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableSyntaxValidator
import document.translation.validation.TableValidationResult
import document.translation.validation.ValidationMode
import org.slf4j.LoggerFactory

/**
 * Leaf-by-leaf AsciiDoc translator (one service call per translatable fragment).
 *
 * XF-FP3-1 — the translator is **stateless**: every pass threads an immutable
 * [TranslationTrace] through the recursive fold and **returns** it with the
 * result ([ArticleTranslation], [BlocksTranslation], [DocumentTranslation]).
 * There is no ambient `currentTopLevelIndex`, no mutable result collection, and
 * no `clear()` for the caller to remember — a reused instance cannot leak the
 * diagnostics of a previous document into the next one.
 */
class DocumentTranslator(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val tableValidationMode: ValidationMode = ValidationMode.LENIENT,
    private val plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT,
    /**
     * US-4 PLT-DIAGRAM-OWNERSHIP (option A) — the N0 [PlantUmlTranslationPort],
     * implemented by the plantuml borough and injected by the orchestrator
     * (bakery). document **delegates** PlantUML label translation to the port and
     * no longer reimplements it (D1/D3). When null, `[plantuml]` blocks are
     * passed through unchanged.
     */
    private val plantUmlPort: PlantUmlTranslationPort? = null,
    /** Document-side syntax validation for the port path (report `DOC-PLANTUML-VALIDATE`). */
    private val plantUmlSyntaxValidator: PlantUmlSyntaxValidator = PlantUmlSyntaxValidator.create(),
) : ArticleTranslator {
    private val log = LoggerFactory.getLogger(DocumentTranslator::class.java)

    override fun translate(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = translateOutcome(asciidoc, sourceLanguage, targetLanguage).content

    /**
     * Translates a whole document and returns the rendered content **with** the
     * diagnostics of the pass (failures, table / PlantUML validation findings).
     */
    internal fun translateOutcome(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): DocumentTranslation {
        val article = parser.parse(asciidoc)
        val translated = translateArticleOutcome(article, sourceLanguage, targetLanguage)
        val outputRenderer = if (article.frontmatter.isJbakeNative) jbakeRenderer else renderer
        return DocumentTranslation(outputRenderer.render(translated.article), translated.trace)
    }

    internal fun translateArticle(
        article: PivotArticle,
        sourceLanguage: String,
        targetLanguage: String,
    ): PivotArticle = translateArticleOutcome(article, sourceLanguage, targetLanguage).article

    internal fun translateArticleOutcome(
        article: PivotArticle,
        sourceLanguage: String,
        targetLanguage: String,
    ): ArticleTranslation {
        val translatedFrontmatter = translateFrontmatter(article.frontmatter, sourceLanguage, targetLanguage)
        val translatedBlocks = translateBlocksOutcome(article.blocks, sourceLanguage, targetLanguage, article.frontmatter.title)
        return ArticleTranslation(
            PivotArticle(translatedFrontmatter, translatedBlocks.blocks),
            translatedBlocks.trace,
        )
    }

    /**
     * Translates a raw list of pivot blocks, preserving non-translatable content
     * (source code, HR, block macros, backtick spans) and keeping the table /
     * PlantUML validation indices consistent with [translateArticleOutcome].
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
    ): List<PivotBlock> = translateBlocksOutcome(blocks, sourceLanguage, targetLanguage, articleTitle).blocks

    internal fun translateBlocksOutcome(
        blocks: List<PivotBlock>,
        sourceLanguage: String,
        targetLanguage: String,
        articleTitle: String = "",
    ): BlocksTranslation {
        var tableIndex = 0
        var plantUmlIndex = 0
        var trace = TranslationTrace.EMPTY
        val translated =
            blocks.mapIndexed { index, block ->
                val outcome =
                    when {
                        block is PivotBlock.Table -> {
                            val result =
                                translateBlock(
                                    block,
                                    sourceLanguage,
                                    targetLanguage,
                                    articleTitle,
                                    tableIndex = tableIndex,
                                    topLevelIndex = index.toString(),
                                )
                            tableIndex++
                            result
                        }
                        block is PivotBlock.Source && block.language == "plantuml" -> {
                            val result =
                                translateBlock(
                                    block,
                                    sourceLanguage,
                                    targetLanguage,
                                    articleTitle,
                                    plantUmlIndex = plantUmlIndex,
                                    topLevelIndex = index.toString(),
                                )
                            plantUmlIndex++
                            result
                        }
                        else ->
                            translateBlock(
                                block,
                                sourceLanguage,
                                targetLanguage,
                                topLevelIndex = index.toString(),
                            )
                    }
                trace = trace.merge(outcome.trace)
                outcome.block
            }
        return BlocksTranslation(translated, trace)
    }

    internal fun translateArticleWithDelta(
        sourceArticle: PivotArticle,
        previousTranslated: PivotArticle,
        delta: BlockDelta,
        sourceLanguage: String,
        targetLanguage: String,
    ): PivotArticle =
        translateArticleWithDeltaOutcome(
            sourceArticle,
            previousTranslated,
            delta,
            sourceLanguage,
            targetLanguage,
        ).article

    internal fun translateArticleWithDeltaOutcome(
        sourceArticle: PivotArticle,
        previousTranslated: PivotArticle,
        delta: BlockDelta,
        sourceLanguage: String,
        targetLanguage: String,
    ): ArticleTranslation {
        if (delta.isEmpty()) return ArticleTranslation(previousTranslated)
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
        var trace = TranslationTrace.EMPTY
        val translatedBlocks =
            sourceArticle.blocks.mapIndexed { idx, block ->
                val outcome =
                    if (idx.toString() in preservedIndices) {
                        previousTranslated.blocks.getOrNull(idx)?.let { BlockTranslation(it) }
                            ?: translateBlock(block, sourceLanguage, targetLanguage, topLevelIndex = idx.toString())
                    } else {
                        when {
                            block is PivotBlock.Table ->
                                translateBlock(
                                    block,
                                    sourceLanguage,
                                    targetLanguage,
                                    sourceArticle.frontmatter.title,
                                    tableIndex = tableIndexByOriginalIndex[idx] ?: 0,
                                    topLevelIndex = idx.toString(),
                                )
                            block is PivotBlock.Source && block.language == "plantuml" ->
                                translateBlock(
                                    block,
                                    sourceLanguage,
                                    targetLanguage,
                                    sourceArticle.frontmatter.title,
                                    plantUmlIndex = plantUmlIndexByOriginalIndex[idx] ?: 0,
                                    topLevelIndex = idx.toString(),
                                )
                            else -> translateBlock(block, sourceLanguage, targetLanguage, topLevelIndex = idx.toString())
                        }
                    }
                trace = trace.merge(outcome.trace)
                outcome.block
            }
        return ArticleTranslation(PivotArticle(translatedFrontmatter, translatedBlocks), trace)
    }

    internal fun translateFrontmatter(
        fm: PivotFrontmatter,
        sourceLanguage: String,
        targetLanguage: String,
    ): PivotFrontmatter {
        val translatedTitle = doTranslate(fm.title, sourceLanguage, targetLanguage).text
        val translatedJbakeAttrs = fm.jbakeAttributes.toMutableMap()
        translatedJbakeAttrs["summary"]?.let {
            translatedJbakeAttrs["summary"] = doTranslate(it, sourceLanguage, targetLanguage).text
        }
        translatedJbakeAttrs["description"]?.let {
            translatedJbakeAttrs["description"] = doTranslate(it, sourceLanguage, targetLanguage).text
        }
        val translatedAsciidocAttrs = fm.asciidocAttributes.toMutableMap()
        translatedAsciidocAttrs["summary"]?.let {
            translatedAsciidocAttrs["summary"] = doTranslate(it, sourceLanguage, targetLanguage).text
        }
        translatedAsciidocAttrs["description"]?.let {
            translatedAsciidocAttrs["description"] = doTranslate(it, sourceLanguage, targetLanguage).text
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
        topLevelIndex: String? = null,
    ): BlockTranslation =
        block.fold(
            heading = {
                val field = doTranslate(it.text, sourceLanguage, targetLanguage)
                BlockTranslation(it.copy(text = field.text), traceOf(topLevelIndex, field))
            },
            paragraph = {
                val inlines = translateInlines(it.inline, sourceLanguage, targetLanguage, topLevelIndex)
                BlockTranslation(it.copy(inline = inlines.inlines), inlines.trace)
            },
            listBlock = {
                var trace = TranslationTrace.EMPTY
                val items =
                    it.items.map { item ->
                        val inlines = translateInlines(item, sourceLanguage, targetLanguage, topLevelIndex)
                        trace = trace.merge(inlines.trace)
                        inlines.inlines
                    }
                BlockTranslation(it.copy(items = items), trace)
            },
            table = {
                var trace = TranslationTrace.EMPTY
                val header =
                    it.header.map { cells ->
                        val inlines = translateInlines(cells, sourceLanguage, targetLanguage, topLevelIndex)
                        trace = trace.merge(inlines.trace)
                        inlines.inlines
                    }
                val rows =
                    it.rows.map { row ->
                        row.map { cells ->
                            val inlines = translateInlines(cells, sourceLanguage, targetLanguage, topLevelIndex)
                            trace = trace.merge(inlines.trace)
                            inlines.inlines
                        }
                    }
                val translated = it.copy(header = header, rows = rows)
                BlockTranslation(translated, trace.merge(validateTranslatedTable(translated, articleTitle, tableIndex)))
            },
            admonition = {
                var trace = TranslationTrace.EMPTY
                val inner =
                    it.blocks.map { b ->
                        val outcome = translateBlock(b, sourceLanguage, targetLanguage, topLevelIndex = topLevelIndex)
                        trace = trace.merge(outcome.trace)
                        outcome.block
                    }
                BlockTranslation(it.copy(blocks = inner), trace)
            },
            source = {
                if (it.language == "plantuml" && plantUmlPort != null) {
                    translatePlantUmlWithPort(it, sourceLanguage, targetLanguage, articleTitle, plantUmlIndex)
                } else {
                    BlockTranslation(it)
                }
            },
            descriptionList = {
                var trace = TranslationTrace.EMPTY
                val items =
                    it.items.map { item ->
                        val term = translateInlines(item.term, sourceLanguage, targetLanguage, topLevelIndex)
                        val definition = translateInlines(item.definition, sourceLanguage, targetLanguage, topLevelIndex)
                        trace = trace.merge(term.trace).merge(definition.trace)
                        DescriptionItem(term = term.inlines, definition = definition.inlines)
                    }
                BlockTranslation(it.copy(items = items), trace)
            },
            blockMacro = { BlockTranslation(it) },
            hr = { BlockTranslation(it) },
        )

    private fun translateInlines(
        inlines: List<PivotInline>,
        sourceLanguage: String,
        targetLanguage: String,
        topLevelIndex: String?,
    ): InlinesTranslation {
        var trace = TranslationTrace.EMPTY
        val translated =
            inlines.map { inline ->
                val outcome = translateInline(inline, sourceLanguage, targetLanguage, topLevelIndex)
                trace = trace.merge(outcome.trace)
                outcome.inline
            }
        return InlinesTranslation(translated, trace)
    }

    private fun translateInline(
        inline: PivotInline,
        sourceLanguage: String,
        targetLanguage: String,
        topLevelIndex: String?,
    ): InlineTranslation =
        when (inline) {
            is PivotInline.Text ->
                translateTranslatableInline(
                    inline.translatable,
                    inline.text,
                    sourceLanguage,
                    targetLanguage,
                    topLevelIndex,
                ) { inline.copy(text = it) }
            is PivotInline.Bold ->
                translateTranslatableInline(
                    inline.translatable,
                    inline.text,
                    sourceLanguage,
                    targetLanguage,
                    topLevelIndex,
                ) { inline.copy(text = it) }
            is PivotInline.Link ->
                translateTranslatableInline(
                    inline.translatable,
                    inline.label,
                    sourceLanguage,
                    targetLanguage,
                    topLevelIndex,
                ) { inline.copy(label = it) }
            is PivotInline.Code -> InlineTranslation(inline)
            is PivotInline.LineBreak -> InlineTranslation(inline)
        }

    private fun translateTranslatableInline(
        translatable: Boolean,
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        topLevelIndex: String?,
        rebuild: (String) -> PivotInline,
    ): InlineTranslation {
        if (!translatable) return InlineTranslation(rebuild(text))
        val field = doTranslate(text, sourceLanguage, targetLanguage)
        return InlineTranslation(rebuild(field.text), traceOf(topLevelIndex, field))
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
    ): BlockTranslation {
        val request =
            PlantUmlTranslationRequest(
                block = PlantUmlBlock(raw = block.content),
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
            )
        val outcome = plantUmlPort!!.translate(request)
        return when (outcome) {
            is PlantUmlTranslationOutcome.Translated -> {
                val translated = block.copy(content = outcome.block.raw)
                // Validate only a translated block (the source was validated upstream
                // by the port's round-trip gate; a preserved block carries no new risk).
                val trace = validateTranslatedPlantUml(translated.content, articleTitle, blockIndex, strategy = "n0-port")
                BlockTranslation(translated, trace)
            }
            is PlantUmlTranslationOutcome.Preserved -> BlockTranslation(block)
        }
    }

    private fun validateTranslatedPlantUml(
        plantumlCode: String,
        articleTitle: String,
        blockIndex: Int,
        strategy: String,
    ): TranslationTrace {
        if (plantUmlValidationMode == ValidationMode.OFF) return TranslationTrace.EMPTY
        val result = plantUmlSyntaxValidator.validate(plantumlCode, articleTitle, blockIndex, strategy)
        if (result is PlantUmlValidationResult.Invalid) {
            val msg = "PlantUML validation failed in article '$articleTitle' block #$blockIndex (strategy=$strategy): ${result.reason}"
            when (plantUmlValidationMode) {
                ValidationMode.STRICT -> throw TranslationException(msg)
                ValidationMode.LENIENT -> log.warn(msg)
                ValidationMode.OFF -> {}
            }
            return TranslationTrace(plantUmlValidationResults = listOf(result))
        }
        return TranslationTrace.EMPTY
    }

    private fun validateTranslatedTable(
        table: PivotBlock.Table,
        articleTitle: String,
        tableIndex: Int,
    ): TranslationTrace {
        if (tableValidationMode == ValidationMode.OFF) return TranslationTrace.EMPTY
        val dddTable = toDddTable(table)
        val result = TableSyntaxValidator.validate(dddTable, articleTitle, tableIndex)
        if (result is TableValidationResult.Invalid) {
            val msg = "Table validation failed in article '$articleTitle' table #$tableIndex: ${result.reason}"
            when (tableValidationMode) {
                ValidationMode.STRICT -> throw TranslationException(msg)
                ValidationMode.LENIENT -> log.warn(msg)
                ValidationMode.OFF -> {}
            }
            return TranslationTrace(tableValidationResults = listOf(result))
        }
        return TranslationTrace.EMPTY
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

    private fun doTranslate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): FieldTranslation {
        if (text.isBlank()) return FieldTranslation(text, failed = false)
        val request = TranslationRequest(text, sourceLanguage, targetLanguage)
        return when (val result = translationService.translate(request)) {
            is TranslationResult.Success -> FieldTranslation(result.translatedText, failed = false)
            // DOC-TRANSLATE-RESILIENCE — the silent French fallback is kept
            // (the rendered article stays complete), but the owning top-level
            // block is reported so it is stored as PENDING, never TRANSLATED.
            is TranslationResult.Failure -> FieldTranslation(text, failed = true)
        }
    }

    /** Attributes a failed field to the current top-level block (if any). */
    private fun traceOf(
        topLevelIndex: String?,
        field: FieldTranslation,
    ): TranslationTrace =
        if (field.failed && topLevelIndex != null) {
            TranslationTrace(translationFailures = setOf(topLevelIndex))
        } else {
            TranslationTrace.EMPTY
        }

    /**
     * Translates a single text field (a title, a label) through the same
     * service the block translation uses, returning the original on failure or
     * blank input.
     *
     * EPIC DOC-BOOK-TRANSLATE — US-1: [document.BookTranslator] reuses this to
     * translate node / section titles without duplicating the fallback policy.
     */
    fun translateField(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = doTranslate(text, sourceLanguage, targetLanguage).text

    /** A translated top-level block and the diagnostics produced while translating it. */
    private data class BlockTranslation(
        val block: PivotBlock,
        val trace: TranslationTrace = TranslationTrace.EMPTY,
    )

    private data class InlinesTranslation(
        val inlines: List<PivotInline>,
        val trace: TranslationTrace = TranslationTrace.EMPTY,
    )

    private data class InlineTranslation(
        val inline: PivotInline,
        val trace: TranslationTrace = TranslationTrace.EMPTY,
    )

    /** Result of one service call: the text to keep, and whether it is a silent fallback. */
    private data class FieldTranslation(
        val text: String,
        val failed: Boolean,
    )
}
