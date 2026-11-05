package document.translation

import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlTranslationPort
import document.translation.delta.BlockChecksum
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockDelta
import document.translation.delta.BlockTranslationStatus
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableValidationResult
import document.translation.validation.ValidationMode
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
    /**
     * US-4 PLT-DIAGRAM-OWNERSHIP (option A) — the N0 PlantUML translation port,
     * built by the orchestrator (bakery) and injected here. document delegates
     * PlantUML blocks to the plantuml borough.
     */
    private val plantUmlPort: PlantUmlTranslationPort? = null,
    /**
     * US-4 PLT-DIAGRAM-OWNERSHIP — PlantUML/table validation strictness. The mode
     * was carried by the (now removed) private adapter; it is exposed here so the
     * orchestrator (bakery) keeps controlling `contentI18nValidation`
     * (STRICT/LENIENT/OFF) for the document-side validation report.
     */
    private val plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT,
    private val tableValidationMode: ValidationMode = ValidationMode.LENIENT,
) {
    private val log = LoggerFactory.getLogger(ContentTranslationService::class.java)

    private val documentTranslator: DocumentTranslator by lazy {
        DocumentTranslator(
            translationService,
            parser,
            renderer,
            jbakeRenderer,
            tableValidationMode = tableValidationMode,
            plantUmlValidationMode = plantUmlValidationMode,
            plantUmlPort = plantUmlPort,
        )
    }

    /**
     * Findings accumulated by *this service instance* from the traces returned by
     * the (stateless) [DocumentTranslator] (XF-FP3-1). The previous design read
     * and cleared mutable collections owned by the translator; here the ownership
     * is local to the run, which is what `drain…` actually means.
     */
    private val tableValidationResults = ConcurrentLinkedQueue<TableValidationResult.Invalid>()
    private val plantUmlValidationResults = ConcurrentLinkedQueue<PlantUmlValidationResult.Invalid>()

    private fun record(trace: TranslationTrace) {
        tableValidationResults.addAll(trace.tableValidationResults)
        plantUmlValidationResults.addAll(trace.plantUmlValidationResults)
    }

    fun translate(
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String,
        excludeRelativePaths: Set<String> = emptySet(),
    ): ContentTranslationResult {
        val adocFiles =
            langDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "adoc" }
                .filter { file ->
                    val relPath = file.relativeTo(langDir).path
                    val dirsToCheck =
                        generateSequence(relPath) { path ->
                            path.lastIndexOf('/').let { if (it > 0) path.substring(0, it) else null }
                        }.toSet()
                    excludeRelativePaths.none { it in dirsToCheck || relPath.startsWith("$it/") }
                }.toList()

        log.info(
            "[translate] Traduction $targetLanguage — ${adocFiles.size} fichiers .adoc dans {} (parallelism={})",
            langDir.name,
            parallelism,
        )

        if (parallelism <= 1) {
            return translateSequential(adocFiles, langDir, sourceLanguage, targetLanguage)
        }
        return translateParallel(adocFiles, langDir, sourceLanguage, targetLanguage)
    }

    fun translateFiles(
        files: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String,
    ): ContentTranslationResult {
        log.info(
            "[translateFiles] Traduction $targetLanguage — ${files.size} fichiers .adoc (parallelism={})",
            parallelism,
        )

        if (parallelism <= 1) {
            return translateSequential(files, langDir, sourceLanguage, targetLanguage)
        }
        return translateParallel(files, langDir, sourceLanguage, targetLanguage)
    }

    private fun translateSequential(
        adocFiles: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String,
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
                val msg = "$relPath: ${e.message}"
                errors.add(msg)
                log.warn("[translate] [{}] ERREUR : {}", targetLanguage, msg)
            }
        }

        log.info(
            "[translate] [{}] Terminé — {} traduits, {} erreurs",
            targetLanguage,
            translated.size,
            errors.size,
        )
        return ContentTranslationResult(translated, errors)
    }

    private fun translateParallel(
        adocFiles: List<File>,
        langDir: File,
        sourceLanguage: String,
        targetLanguage: String,
    ): ContentTranslationResult =
        runBlocking {
            val translated = ConcurrentLinkedQueue<String>()
            val errors = ConcurrentLinkedQueue<String>()
            val semaphore = Semaphore(parallelism)

            coroutineScope {
                adocFiles
                    .mapIndexed { idx, file ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val relPath = file.relativeTo(langDir).path
                                log.info("[translate] [{}] {}/{} Traduction de : {}", targetLanguage, idx + 1, adocFiles.size, relPath)
                                try {
                                    translateSingleFile(file, sourceLanguage, targetLanguage)
                                    translated.add(relPath)
                                    log.info("[translate] [{}] OK : {}", targetLanguage, relPath)
                                } catch (e: Exception) {
                                    val msg = "$relPath: ${e.message}"
                                    errors.add(msg)
                                    log.warn("[translate] [{}] ERREUR : {}", targetLanguage, msg)
                                }
                            }
                        }
                    }.awaitAll()
            }

            log.info(
                "[translate] [{}] Terminé — {} traduits, {} erreurs",
                targetLanguage,
                translated.size,
                errors.size,
            )
            ContentTranslationResult(translated.toList(), errors.toList())
        }

    private fun translateSingleFile(
        file: File,
        sourceLanguage: String,
        targetLanguage: String,
    ) {
        val original = file.readText()
        val sourceArticle = parser.parse(original)
        val outcome = documentTranslator.translateOutcome(original, sourceLanguage, targetLanguage)
        record(outcome.trace)
        val renderedBlocks = parser.parse(outcome.content).blocks
        // DOC-OUT-2 — never write a structurally duplicated/truncated artefact.
        TranslationOutputIntegrity.requireNoDuplication(
            sourceBlocks = sourceArticle.blocks,
            renderedBlocks = renderedBlocks,
            articleLabel = file.name,
        )
        // Structure is translation-invariant: a translated text that introduces
        // a heading/table/delimiter must fail here, before the source is replaced.
        requireSameBlockStructure(
            sourceBlocks = sourceArticle.blocks,
            renderedBlocks = renderedBlocks,
            sourceLabel = file.path,
        )
        file.writeText(outcome.content)
    }

    fun translateSingleFileWithBlockDelta(
        sourceFile: File,
        targetFile: File,
        previousBlockChecksums: Map<String, BlockChecksumEntry>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, BlockChecksumEntry> {
        val sourceText = sourceFile.readText()
        val sourceArticle = parser.parse(sourceText)
        val currentBlockHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val trace: TranslationTrace =
            if (previousBlockChecksums.isEmpty() || !targetFile.exists()) {
                val translated = documentTranslator.translateOutcome(sourceText, sourceLanguage, targetLanguage)
                val renderedBlocks = parser.parse(translated.content).blocks
                // DOC-OUT-2 — a re-translation must not duplicate blocks.
                TranslationOutputIntegrity.requireNoDuplication(
                    sourceBlocks = sourceArticle.blocks,
                    renderedBlocks = renderedBlocks,
                    articleLabel = sourceFile.name,
                )
                requireSameBlockStructure(
                    sourceBlocks = sourceArticle.blocks,
                    renderedBlocks = renderedBlocks,
                    sourceLabel = sourceFile.path,
                )
                targetFile.writeText(translated.content)
                translated.trace
            } else {
                val previousTranslatedArticle = parser.parse(targetFile.readText())
                val delta = BlockDelta.compute(previous = previousBlockChecksums, current = currentBlockHashes)
                if (delta.isEmpty()) {
                    // No block changed: the previous statuses are carried over by the
                    // caller (the stored map is compared hash-by-hash) — but a PENDING
                    // block with an unchanged hash is re-translated by BlockDelta, so
                    // an empty delta means every block is genuinely TRANSLATED.
                    return currentBlockHashes.mapValues {
                        BlockChecksumEntry(it.value, BlockTranslationStatus.TRANSLATED)
                    }
                }
                val translatedArticle =
                    documentTranslator.translateArticleWithDeltaOutcome(
                        sourceArticle,
                        previousTranslatedArticle,
                        delta,
                        sourceLanguage,
                        targetLanguage,
                    )
                val outputRenderer = if (sourceArticle.frontmatter.isJbakeNative) jbakeRenderer else renderer
                // Render to a string first: the guard must run on what would
                // actually be written, not on the pre-render pivot AST.
                val rendered = outputRenderer.render(translatedArticle.article)
                val renderedBlocks = parser.parse(rendered).blocks
                // DOC-OUT-2 — the delta path is where a block could be re-inserted
                // more than once; fail before writing the artefact.
                TranslationOutputIntegrity.requireNoDuplication(
                    sourceBlocks = sourceArticle.blocks,
                    renderedBlocks = renderedBlocks,
                    articleLabel = sourceFile.name,
                )
                requireSameBlockStructure(
                    sourceBlocks = sourceArticle.blocks,
                    renderedBlocks = renderedBlocks,
                    sourceLabel = sourceFile.path,
                )
                targetFile.writeText(rendered)
                translatedArticle.trace
            }
        record(trace)
        // DOC-TRANSLATE-RESILIENCE — a block whose LLM call failed is stored as
        // PENDING, never TRANSLATED: the next delta re-attempts it instead of
        // freezing the silent French fallback (BlockDelta preserves a TRANSLATED
        // block whose hash is unchanged).
        return BlockChecksum.resolveStatuses(currentBlockHashes, trace.translationFailures)
    }

    fun retranslateFrontmatter(
        sourceFile: File,
        targetFile: File,
        sourceLanguage: String,
        targetLanguage: String,
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
        val retranslatedFrontmatter =
            documentTranslator.translateFrontmatter(
                sourceArticle.frontmatter,
                sourceLanguage,
                targetLanguage,
            )
        val mergedArticle = PivotArticle(retranslatedFrontmatter, targetArticle.blocks)
        val outputRenderer = if (sourceArticle.frontmatter.isJbakeNative) jbakeRenderer else renderer
        targetFile.writeText(outputRenderer.render(mergedArticle))
        return FrontmatterRetranslationResult(retranslated = true, staleKeys = staleReport.staleKeys)
    }

    internal fun translateArticle(
        article: PivotArticle,
        sourceLanguage: String,
        targetLanguage: String,
    ): PivotArticle {
        val outcome = documentTranslator.translateArticleOutcome(article, sourceLanguage, targetLanguage)
        record(outcome.trace)
        return outcome.article
    }

    /**
     * Structural guard: the rendered variant must keep the source block
     * signature (count **and** kind sequence). A translated text that opens a new
     * heading/table/block or closes/loses one fails here, before the target file
     * is written — a corrupt variant is never persisted.
     */
    private fun requireSameBlockStructure(
        sourceBlocks: List<PivotBlock>,
        renderedBlocks: List<PivotBlock>,
        sourceLabel: String,
    ) {
        val expected = DocumentTranslator.structureSignature(sourceBlocks)
        val actual = DocumentTranslator.structureSignature(renderedBlocks)
        if (expected == actual) return
        val index = firstStructureDivergence(expected, actual)
        val expectedKind = expected.getOrNull(index) ?: "<end-of-document>"
        val actualKind = actual.getOrNull(index) ?: "<end-of-document>"
        throw IllegalStateException(
            "Translation of '$sourceLabel' changed the AsciiDoc block structure at index $index: " +
                "the source expects '$expectedKind' (${expected.size} blocks) but the rendered variant " +
                "has '$actualKind' (${actual.size} blocks). Translation must only replace text, never " +
                "introduce or drop a block; the target file was not written.",
        )
    }

    private fun firstStructureDivergence(
        expected: List<String>,
        actual: List<String>,
    ): Int {
        val indices = 0 until maxOf(expected.size, actual.size)
        return indices.firstOrNull { expected.getOrNull(it) != actual.getOrNull(it) }
            ?: maxOf(expected.size, actual.size)
    }

    fun drainTableValidationResults(): List<TableValidationResult.Invalid> {
        val results = tableValidationResults.toList()
        tableValidationResults.clear()
        return results
    }

    fun drainPlantUmlValidationResults(): List<PlantUmlValidationResult.Invalid> {
        val results = plantUmlValidationResults.toList()
        plantUmlValidationResults.clear()
        return results
    }
}

data class ContentTranslationResult(
    val filesTranslated: List<String>,
    val errors: List<String>,
) {
    val success: Boolean get() = errors.isEmpty()
}

data class FrontmatterRetranslationResult(
    val retranslated: Boolean,
    val staleKeys: Set<String>,
)
