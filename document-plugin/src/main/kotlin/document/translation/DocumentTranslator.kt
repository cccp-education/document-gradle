package document.translation

import document.translation.plantuml.PlantUmlTranslationAdapter
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService

class DocumentTranslator(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val plantUmlAdapter: PlantUmlTranslationAdapter? = null
) {

    fun translate(
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
        val translatedBlocks = article.blocks.map { translateBlock(it, sourceLanguage, targetLanguage) }
        return PivotArticle(translatedFrontmatter, translatedBlocks)
    }

    private fun translateFrontmatter(
        fm: PivotFrontmatter,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotFrontmatter {
        val translatedTitle = doTranslate(fm.title, sourceLanguage, targetLanguage)
        return fm.copy(title = translatedTitle)
    }

    private fun translateBlock(
        block: PivotBlock,
        sourceLanguage: String,
        targetLanguage: String
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
            block.copy(
                header = block.header.map { cells ->
                    translateInlines(cells, sourceLanguage, targetLanguage)
                },
                rows = block.rows.map { row ->
                    row.map { cells ->
                        translateInlines(cells, sourceLanguage, targetLanguage)
                    }
                }
            )
        }
        is PivotBlock.Admonition -> {
            block.copy(
                blocks = block.blocks.map { translateBlock(it, sourceLanguage, targetLanguage) }
            )
        }
        is PivotBlock.Source -> {
            if (block.language == "plantuml" && plantUmlAdapter != null) {
                plantUmlAdapter.translate(block, sourceLanguage, targetLanguage)
            } else {
                block
            }
        }
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
