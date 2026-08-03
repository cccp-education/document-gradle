package document.translation

import document.translation.validation.ValidationMode
import contracts.i18n.TranslationService
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

@DisableCachingByDefault(because = "Batch translation output depends on LLM which is non-deterministic")
abstract class TranslateDocumentBatchTask : DefaultTask {

    constructor() {
        group = "document"
        description = "Batch-translates all AsciiDoc files in a directory from source language to target language via LLM. — DOC-TRANSLATE-BATCH"
        sourceLanguage.convention("fr")
        targetLanguage.convention("en")
        llmMode.convention("ollama")
        excludePaths.convention("")
        tableValidationMode.convention("LENIENT")
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val sourceLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val targetLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val llmMode: Property<String>

    @get:Input
    @get:Optional
    abstract val excludePaths: Property<String>

    @get:Input
    @get:Optional
    abstract val treeMode: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val skipExisting: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val tableValidationMode: Property<String>

    @TaskAction
    fun translateBatch() {
        val logger = LoggerFactory.getLogger(TranslateDocumentBatchTask::class.java)
        val src = sourceDir.get().asFile
        val out = outputDir.get().asFile
        val srcLang = sourceLanguage.get()
        val tgtLang = targetLanguage.get()
        val mode = llmMode.get()
        val tree = treeMode.getOrElse(false)
        val skip = skipExisting.getOrElse(false)
        val excludesRaw = excludePaths.get().trim()
        val excludes = if (excludesRaw.isEmpty()) emptySet()
            else excludesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val validationMode = try {
            ValidationMode.valueOf(tableValidationMode.get().uppercase())
        } catch (_: IllegalArgumentException) {
            ValidationMode.LENIENT
        }

        logger.info("translateDocumentBatch — {} ({}→{}) treeMode={} skipExisting={} excludes={} tableValidation={}", src.absolutePath, srcLang, tgtLang, tree, skip, excludes, validationMode)

        val translationService: TranslationService = when (mode.lowercase()) {
            "fake" -> if (tree) NumberedFakeTranslationService() else FakeTranslationService(" [${tgtLang.uppercase()}]")
            "ollama" -> PooledOllamaTranslationAdapter.create()
            else -> throw IllegalArgumentException("Unknown llmMode: '$mode' — expected 'ollama' or 'fake'")
        }

        val batchTranslator = if (tree) {
            BatchDocumentTranslator(TreeTranslationAdapter(translationService))
        } else {
            BatchDocumentTranslator(DocumentTranslator(translationService, tableValidationMode = validationMode))
        }
        val result = batchTranslator.translateBatch(
            BatchTranslationRequest(src, out, srcLang, tgtLang, excludes, skip),
        )

        logger.info("translateDocumentBatch done — {} translated, {} errors", result.count, result.errors.size)
        if (result.hasErrors) {
            result.errors.forEach { logger.warn("  ERROR: {}", it) }
        }
    }
}