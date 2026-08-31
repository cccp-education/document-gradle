package document.translation

import contracts.i18n.TranslationService
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

@DisableCachingByDefault(because = "Frontmatter re-translation depends on LLM which is non-deterministic")
abstract class RetranslateFrontmatterTask : DefaultTask() {

    @get:org.gradle.api.tasks.InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:org.gradle.api.tasks.Input
    abstract val targetDirPath: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val targetLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val llmMode: Property<String>

    init {
        group = "document"
        description = "Re-translates stale frontmatter (title/summary/description) in target AsciiDoc files, preserving already-translated body blocks (economie d'encre)."
        sourceLanguage.convention("fr")
        targetLanguage.convention("en")
        llmMode.convention("ollama")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun retranslate() {
        val logger = LoggerFactory.getLogger(RetranslateFrontmatterTask::class.java)
        val srcDir = sourceDir.get().asFile
        val tgtDir = project.file(targetDirPath.get())
        val srcLang = sourceLanguage.get()
        val tgtLang = targetLanguage.get()
        val mode = llmMode.get()

        val translationService: TranslationService = when (mode.lowercase()) {
            "fake" -> FakeTranslationService(" [${tgtLang.uppercase()}]")
            "ollama" -> PooledOllamaTranslationAdapter.create()
            else -> throw IllegalArgumentException("Unknown llmMode: '$mode' — expected 'ollama' or 'fake'")
        }

        val service = ContentTranslationService(translationService)

        val targetFiles = tgtDir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .toList()

        var retranslatedCount = 0
        var preservedCount = 0
        var skippedCount = 0

        for ((idx, targetFile) in targetFiles.withIndex()) {
            val relPath = targetFile.relativeTo(tgtDir).path
            val sourceFile = srcDir.resolve(relPath)
            val effectiveSourceFile = if (sourceFile.exists()) {
                sourceFile
            } else {
                val fileName = targetFile.name
                srcDir.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
            }
            if (effectiveSourceFile == null || !effectiveSourceFile.exists()) {
                logger.info("[retranslateFrontmatter] {}/{} SKIP (source not found): {}",
                    idx + 1, targetFiles.size, relPath)
                skippedCount++
                continue
            }
            val result = service.retranslateFrontmatter(effectiveSourceFile, targetFile, srcLang, tgtLang)
            if (result.retranslated) {
                logger.info("[retranslateFrontmatter] {}/{} RETRANSLATED (stale keys={}): {}",
                    idx + 1, targetFiles.size, result.staleKeys, relPath)
                retranslatedCount++
            } else {
                logger.info("[retranslateFrontmatter] {}/{} PRESERVED (not stale): {}",
                    idx + 1, targetFiles.size, relPath)
                preservedCount++
            }
        }

        logger.info(
            "[retranslateFrontmatter] {} — {} retranslated, {} preserved, {} skipped (source={}, target={})",
            tgtLang, retranslatedCount, preservedCount, skippedCount, srcDir.name, tgtDir.name
        )
    }
}