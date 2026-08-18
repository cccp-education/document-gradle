package document.translation

import document.translation.delta.BlockChecksum
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockDelta
import document.translation.delta.BlockTranslationStatus
import document.translation.plantuml.PlantUmlTranslationAdapter
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableValidationResult
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

    private val documentTranslator: DocumentTranslator by lazy {
        DocumentTranslator(translationService, parser, renderer, jbakeRenderer, plantUmlAdapter)
    }

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
        val rendered = documentTranslator.translate(original, sourceLanguage, targetLanguage)
        file.writeText(rendered)
    }

    fun translateSingleFileWithBlockDelta(
        sourceFile: File,
        targetFile: File,
        previousBlockChecksums: Map<String, BlockChecksumEntry>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, BlockChecksumEntry> {
        val sourceText = sourceFile.readText()
        val sourceArticle = parser.parse(sourceText)
        val currentBlockHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        if (previousBlockChecksums.isEmpty() || !targetFile.exists()) {
            val rendered = documentTranslator.translate(sourceText, sourceLanguage, targetLanguage)
            targetFile.writeText(rendered)
            return currentBlockHashes.mapValues { BlockChecksumEntry(it.value, BlockTranslationStatus.TRANSLATED) }
        }
        val previousTranslatedArticle = parser.parse(targetFile.readText())
        val delta = BlockDelta.compute(previous = previousBlockChecksums, current = currentBlockHashes)
        if (delta.isEmpty()) {
            return currentBlockHashes.mapValues { BlockChecksumEntry(it.value, BlockTranslationStatus.TRANSLATED) }
        }
        val translatedArticle = documentTranslator.translateArticleWithDelta(
            sourceArticle, previousTranslatedArticle, delta, sourceLanguage, targetLanguage
        )
        val outputRenderer = if (sourceArticle.frontmatter.isJbakeNative) jbakeRenderer else renderer
        targetFile.writeText(outputRenderer.render(translatedArticle))
        return currentBlockHashes.mapValues { BlockChecksumEntry(it.value, BlockTranslationStatus.TRANSLATED) }
    }

    fun retranslateFrontmatter(
        sourceFile: File,
        targetFile: File,
        sourceLanguage: String,
        targetLanguage: String
    ): FrontmatterRetranslationResult {
        if (!targetFile.exists()) {
            return FrontmatterRetranslationResult(retranslated = false, staleKeys = emptySet())
        }
        val sourceArticle = parser.parse(sourceFile.readText())
        val targetArticle = parser.parse(targetFile.readText())
        val staleReport = FrontmatterStaleDetector.detect(sourceArticle.frontmatter, targetArticle.frontmatter)
        if (!staleReport.stale) {
            return FrontmatterRetranslationResult(retranslated = false, staleKeys = emptySet())
        }
        val retranslatedFrontmatter = documentTranslator.translateFrontmatter(
            sourceArticle.frontmatter, sourceLanguage, targetLanguage
        )
        val mergedArticle = PivotArticle(retranslatedFrontmatter, targetArticle.blocks)
        val outputRenderer = if (sourceArticle.frontmatter.isJbakeNative) jbakeRenderer else renderer
        targetFile.writeText(outputRenderer.render(mergedArticle))
        return FrontmatterRetranslationResult(retranslated = true, staleKeys = staleReport.staleKeys)
    }

    internal fun translateArticle(
        article: PivotArticle,
        sourceLanguage: String,
        targetLanguage: String
    ): PivotArticle = documentTranslator.translateArticle(article, sourceLanguage, targetLanguage)

    fun drainTableValidationResults(): List<TableValidationResult.Invalid> {
        val results = documentTranslator.tableValidationResults.toList()
        documentTranslator.tableValidationResults.clear()
        return results
    }

    fun drainPlantUmlValidationResults(): List<PlantUmlValidationResult.Invalid> {
        val results = documentTranslator.plantUmlValidationResults.toList()
        documentTranslator.plantUmlValidationResults.clear()
        return results
    }
}

data class ContentTranslationResult(
    val filesTranslated: List<String>,
    val errors: List<String>
) {
    val success: Boolean get() = errors.isEmpty()
}

data class FrontmatterRetranslationResult(
    val retranslated: Boolean,
    val staleKeys: Set<String>
)
