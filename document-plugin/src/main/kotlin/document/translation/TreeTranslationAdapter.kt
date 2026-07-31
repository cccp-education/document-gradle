package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.slf4j.LoggerFactory

/**
 * Whole-article translation strategy.
 *
 * Collects every translatable leaf (paragraph text, list item, table cell,
 * heading, frontmatter title/summary/description) of the parsed pivot tree,
 * sends them to the LLM as one numbered batch, then reassembles the translated
 * leaves back into the tree — a single LLM call per article instead of one per
 * leaf. Non-translatable blocks (source code, HR, URLs, backtick spans) never
 * reach the LLM and are preserved by construction.
 */
class TreeTranslationAdapter(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val batchPrompt: (TranslationRequest) -> String = ::buildBatchPrompt,
    private val parseNumberedLines: (String) -> Map<Int, String> = ::parseNumberedLines,
) : ArticleTranslator {

    private val log = LoggerFactory.getLogger(TreeTranslationAdapter::class.java)

    override fun translate(asciidoc: String, sourceLanguage: String, targetLanguage: String): String {
        val article = parser.parse(asciidoc)

        // 1. Collect leaves (ordered), skipping non-translatable content.
        val collector = LeafCollector()
        val leaves = collector.collect(article)

        if (leaves.isEmpty()) return asciidoc

        // 2. Build one numbered batch prompt.
        val numbered = leaves.mapIndexed { idx, leaf -> "${idx + 1}: ${leaf.text}" }.joinToString("\n")
        val request = TranslationRequest(numbered, sourceLanguage, targetLanguage)
        val result = translationService.translate(request)

        return when (result) {
            is TranslationResult.Failure -> {
                log.warn("[TreeTranslationAdapter] LLM call failed ({}), returning original", result.reason)
                asciidoc
            }
            is TranslationResult.Success -> {
                val translations = try {
                    parseNumberedLines(result.translatedText)
                } catch (e: Exception) {
                    log.warn("[TreeTranslationAdapter] Failed to parse LLM numbered output: {}", e.message)
                    emptyMap()
                }
                if (translations.isEmpty()) {
                    log.warn("[TreeTranslationAdapter] No translations parsed, returning original")
                    return asciidoc
                }

                // 3. Reassemble in order.
                val applier = LeafApplier(translations)
                val translated = applier.apply(article)
                val outputRenderer = if (translated.frontmatter.isJbakeNative) jbakeRenderer else renderer
                outputRenderer.render(translated)
            }
        }
    }

    private data class Leaf(val text: String, val path: LeafPath)

    private sealed class LeafPath {
        data class Frontmatter(val field: String) : LeafPath()
        data class Inline(
            val blockIndex: Int,
            val subPath: List<Int>,   // indexes into nested structures (list items, table cells, admonition blocks)
            val inlineIndex: Int,
        ) : LeafPath()
    }

    private class LeafCollector {
        fun collect(article: PivotArticle): List<Leaf> {
            val result = mutableListOf<Leaf>()
            val fm = article.frontmatter
            if (fm.title.isNotBlank()) {
                result.add(Leaf(fm.title, LeafPath.Frontmatter("title")))
            }
            fm.jbakeAttributes["summary"]?.let { if (it.isNotBlank()) result.add(Leaf(it, LeafPath.Frontmatter("summary"))) }
            fm.jbakeAttributes["description"]?.let { if (it.isNotBlank()) result.add(Leaf(it, LeafPath.Frontmatter("description"))) }
            fm.asciidocAttributes["summary"]?.let { if (it.isNotBlank()) result.add(Leaf(it, LeafPath.Frontmatter("asciidoc_summary"))) }
            fm.asciidocAttributes["description"]?.let { if (it.isNotBlank()) result.add(Leaf(it, LeafPath.Frontmatter("asciidoc_description"))) }

            article.blocks.forEachIndexed { blockIndex, block ->
                collectBlock(block, blockIndex, emptyList(), result)
            }
            return result
        }

        private fun collectBlock(
            block: PivotBlock,
            blockIndex: Int,
            subPath: List<Int>,
            out: MutableList<Leaf>,
        ) {
            when (block) {
                is PivotBlock.Heading -> {
                    if (block.translatable && block.text.isNotBlank()) {
                        out.add(Leaf(block.text, LeafPath.Inline(blockIndex, subPath, -1)))
                    }
                }
                is PivotBlock.Paragraph -> collectInlines(block.inline, blockIndex, subPath, out)
                is PivotBlock.ListBlock -> {
                    block.items.forEachIndexed { itemIdx, item ->
                        collectInlines(item, blockIndex, subPath + itemIdx, out)
                    }
                }
                is PivotBlock.Table -> {
                    block.header.forEachIndexed { rowIdx, cells ->
                        collectInlines(cells, blockIndex, subPath + listOf(-10 - rowIdx), out)
                    }
                    block.rows.forEachIndexed { rowIdx, cells ->
                        cells.forEachIndexed { cellIdx, cell ->
                            collectInlines(cell, blockIndex, subPath + listOf(rowIdx, cellIdx), out)
                        }
                    }
                }
                is PivotBlock.Admonition -> {
                    block.blocks.forEachIndexed { innerIdx, inner ->
                        collectBlock(inner, blockIndex, subPath + listOf(-20 - innerIdx), out)
                    }
                }
                is PivotBlock.Source, is PivotBlock.Hr -> Unit
            }
        }

        private fun collectInlines(
            inlines: List<PivotInline>,
            blockIndex: Int,
            subPath: List<Int>,
            out: MutableList<Leaf>,
        ) {
            inlines.forEachIndexed { inlineIndex, inline ->
                val text = inlineText(inline) ?: return@forEachIndexed
                if (inline.translatable && text.isNotBlank()) {
                    out.add(Leaf(text, LeafPath.Inline(blockIndex, subPath, inlineIndex)))
                }
            }
        }

        private fun inlineText(inline: PivotInline): String? = when (inline) {
            is PivotInline.Text -> inline.text
            is PivotInline.Bold -> inline.text
            is PivotInline.Code -> null
            is PivotInline.Link -> inline.label
            is PivotInline.LineBreak -> null
        }
    }

    private class LeafApplier(private val translations: Map<Int, String>) {
        private var idx = 0

        fun apply(article: PivotArticle): PivotArticle {
            val fm = article.frontmatter
            val title = if (fm.title.isNotBlank()) replace(fm.title) else fm.title
            val jbake = fm.jbakeAttributes.toMutableMap()
            listOf("summary", "description").forEach { key ->
                jbake[key]?.let { if (it.isNotBlank()) jbake[key] = replace(it) }
            }
            val asciidoc = fm.asciidocAttributes.toMutableMap()
            listOf("summary", "description").forEach { key ->
                asciidoc[key]?.let { if (it.isNotBlank()) asciidoc[key] = replace(it) }
            }

            val newFm = fm.copy(title = title, jbakeAttributes = jbake, asciidocAttributes = asciidoc)
            val blocks = article.blocks.map { block -> applyBlock(block) }
            return PivotArticle(newFm, blocks)
        }

        private fun applyBlock(block: PivotBlock): PivotBlock = when (block) {
            is PivotBlock.Heading -> {
                if (block.translatable && block.text.isNotBlank()) block.copy(text = replace(block.text)) else block
            }
            is PivotBlock.Paragraph -> block.copy(inline = applyInlines(block.inline))
            is PivotBlock.ListBlock -> block.copy(items = block.items.map { applyInlines(it) })
            is PivotBlock.Table -> block.copy(
                header = block.header.map { applyInlines(it) },
                rows = block.rows.map { row -> row.map { applyInlines(it) } },
            )
            is PivotBlock.Admonition -> block.copy(blocks = block.blocks.map { applyBlock(it) })
            is PivotBlock.Source, is PivotBlock.Hr -> block
        }

        private fun applyInlines(inlines: List<PivotInline>): List<PivotInline> = inlines.map { inline ->
            when (inline) {
                is PivotInline.Text -> if (inline.translatable && inline.text.isNotBlank()) inline.copy(text = replace(inline.text)) else inline
                is PivotInline.Bold -> if (inline.translatable && inline.text.isNotBlank()) inline.copy(text = replace(inline.text)) else inline
                is PivotInline.Link -> if (inline.translatable && inline.label.isNotBlank()) inline.copy(label = replace(inline.label)) else inline
                is PivotInline.Code, is PivotInline.LineBreak -> inline
            }
        }

        private fun replace(original: String): String {
            val n = idx
            idx++
            return translations[n + 1] ?: original
        }
    }

    companion object {
        /**
         * Prompt asking the LLM to translate a numbered list of text fragments.
         * Each line is `N: text`. The model must reply with `N: translation` lines.
         */
        fun buildBatchPrompt(request: TranslationRequest): String =
            """You are a professional technical translator.
Translate the following numbered list of text fragments from ${request.sourceLanguage} to ${request.targetLanguage}.

Rules:
- Each input line is: N: <text>. Reply with exactly the same N and the translated text: N: <translated>.
- Keep every number N unchanged.
- Preserve ALL backtick code spans (`...`) exactly as-is — never modify backtick content, spacing, or position.
- A fragment may be part of a larger sentence — translate the fragment itself without asking for context.
- If a fragment is already fully in ${request.targetLanguage}, keep it unchanged.
- Output ONLY the numbered translations, one per line, no explanation, no intro, no options.

${request.sourceText}"""

        /**
         * Parses `N: text` numbered lines (the reply to [buildBatchPrompt]).
         * Tolerates prefixes like `- N:` and `N. text`, and LLM noise around the list.
         */
        fun parseNumberedLines(response: String): Map<Int, String> {
            val result = mutableMapOf<Int, String>()
            val pattern = Regex("""^[-*\s]*(\d+)\s*[:.)-]\s*(.+)$""")
            response.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val m = pattern.matchEntire(line)
                if (m != null) {
                    val n = m.groupValues[1].toInt()
                    val text = m.groupValues[2].trim()
                    if (text.isNotEmpty()) {
                        result[n] = text
                    }
                }
            }
            return result
        }
    }
}
