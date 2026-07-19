package document.translation

import document.translation.plantuml.PlantUmlTranslationAdapter
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class ContentTranslationService(
    private val translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val renderer: ArticleRenderer = AsciiDocRenderer(),
    private val jbakeRenderer: ArticleRenderer = JbakeNativeRenderer(),
    private val parallelism: Int = 1,
    private val plantUmlAdapter: PlantUmlTranslationAdapter? = null
) {
    private val log = LoggerFactory.getLogger(ContentTranslationService::class.java)

    fun translate(
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String,
        excludeRelativePaths: Set<String> = emptySet()
    ): ContentTranslationResult {
        val adocFiles = langDir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .filter { file ->
                val relPath = file.relativeTo(langDir).path
                val dirsToCheck = generateSequence(relPath) { path ->
                    path.lastIndexOf('/').let { if (it > 0) path.substring(0, it) else null }
                }.toSet()
                excludeRelativePaths.none { it in dirsToCheck || relPath.startsWith("$it/") }
            }
            .toList()

        log.info("[translate] Traduction $targetLanguage — ${adocFiles.size} fichiers .adoc dans {} (parallelism={})",
            langDir.name, parallelism)

        if (parallelism <= 1) {
            return translateSequential(adocFiles, langDir, sourceLanguage, targetLanguage)
        }
        return translateParallel(adocFiles, langDir, sourceLanguage, targetLanguage)
    }

    fun translateFiles(
        files: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String
    ): ContentTranslationResult {
        log.info("[translateFiles] Traduction $targetLanguage — ${files.size} fichiers .adoc (parallelism={})",
            parallelism)

        if (parallelism <= 1) {
            return translateSequential(files, langDir, sourceLanguage, targetLanguage)
        }
        return translateParallel(files, langDir, sourceLanguage, targetLanguage)
    }

    private fun translateSequential(
        adocFiles: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String
    ): ContentTranslationResult {
        val translated = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for ((idx, file) in adocFiles.withIndex()) {
            val relPath = file.relativeTo(langDir).path
            log.info("[translate] [{}] {}/{} Traduction de : {}", targetLanguage, idx + 1, adocFiles.size, relPath)
            try {
                translateSingleFile(file, sourceLanguage, targetLanguage)
                translated.add(relPath)
                log.info("[translate] [{}] OK : {}", targetLanguage, relPath)
            } catch (e: Exception) {
                val msg = "${relPath}: ${e.message}"
                errors.add(msg)
                log.warn("[translate] [{}] ERREUR : {}", targetLanguage, msg)
            }
        }

        log.info("[translate] [{}] Terminé — {} traduits, {} erreurs",
            targetLanguage, translated.size, errors.size)
        return ContentTranslationResult(translated, errors)
    }

    private fun translateParallel(
        adocFiles: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String
    ): ContentTranslationResult = runBlocking {
        val translated = ConcurrentLinkedQueue<String>()
        val errors = ConcurrentLinkedQueue<String>()
        val semaphore = Semaphore(parallelism)

        coroutineScope {
            adocFiles.mapIndexed { idx, file ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val relPath = file.relativeTo(langDir).path
                        log.info("[translate] [{}] {}/{} Traduction de : {}", targetLanguage, idx + 1, adocFiles.size, relPath)
                        try {
                            translateSingleFile(file, sourceLanguage, targetLanguage)
                            translated.add(relPath)
                            log.info("[translate] [{}] OK : {}", targetLanguage, relPath)
                        } catch (e: Exception) {
                            val msg = "${relPath}: ${e.message}"
                            errors.add(msg)
                            log.warn("[translate] [{}] ERREUR : {}", targetLanguage, msg)
                        }
                    }
                }
            }.awaitAll()
        }

        log.info("[translate] [{}] Terminé — {} traduits, {} erreurs",
            targetLanguage, translated.size, errors.size)
        ContentTranslationResult(translated.toList(), errors.toList())
    }

    private fun translateSingleFile(
        file: File,
        sourceLanguage: String,
        targetLanguage: String
    ) {
        val original = file.readText()
        val article = parser.parse(original)
        val translatedArticle = translateArticle(article, sourceLanguage, targetLanguage)
        val outputRenderer = if (article.frontmatter.isJbakeNative) jbakeRenderer else renderer
        val rendered = outputRenderer.render(translatedArticle)
        file.writeText(rendered)
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

data class ContentTranslationResult(
    val filesTranslated: List<String>,
    val errors: List<String>
) {
    val success: Boolean get() = errors.isEmpty()
}
