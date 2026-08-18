package document.translation

import document.translation.delta.BlockDelta
import document.translation.plantuml.PlantUmlTranslationAdapter
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableSyntaxValidator
import document.translation.validation.TableValidationResult
import document.translation.validation.ValidationMode
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.slf4j.LoggerFactory

class DocumentTranslator(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val plantUmlAdapter: PlantUmlTranslationAdapter? = null,
    private val tableValidationMode: ValidationMode = ValidationMode.LENIENT,
    private val plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT,
) : ArticleTranslator {

    private val log = LoggerFactory.getLogger(DocumentTranslator::class.java)

    val tableValidationResults: MutableList<TableValidationResult.Invalid> = mutableListOf()
    val plantUmlValidationResults: MutableList<PlantUmlValidationResult.Invalid> = mutableListOf()

    override fun translate(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
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
        var tableIndex = 0
        var plantUmlIndex = 0
        val translatedBlocks = article.blocks.map { block ->
            when {
                block is PivotBlock.Table -> {
                    val result = translateBlock(block, sourceLanguage, targetLanguage, article.frontmatter.title, tableIndex)
                    tableIndex++
                    result
                }
                block is PivotBlock.Source && block.language == "plantuml" -> {
                    val result = translateBlock(block, sourceLanguage, targetLanguage, article.frontmatter.title, plantUmlIndex = plantUmlIndex)
                    plantUmlIndex++
                    result
                }
                else -> translateBlock(block, sourceLanguage, targetLanguage)
            }
        }
        return PivotArticle(translatedFrontmatter, translatedBlocks)
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
            }
        }
        return PivotArticle(translatedFrontmatter, translatedBlocks)
    }

    private fun translateFrontmatter(
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
            if (block.language == "plantuml" && plantUmlAdapter != null) {
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
            is TranslationResult.Failure -> text
        }
    }
}
