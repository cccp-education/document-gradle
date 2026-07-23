package document.translation

import org.slf4j.LoggerFactory
import java.io.File

class BatchDocumentTranslator(
    private val documentTranslator: DocumentTranslator,
) {
    private val log = LoggerFactory.getLogger(BatchDocumentTranslator::class.java)

    fun translateBatch(request: BatchTranslationRequest): BatchTranslationResult {
        val sourceDir = request.sourceDir
        val outputDir = request.outputDir
        val excludes = request.excludeRelativePaths
        val srcLang = request.sourceLanguage
        val tgtLang = request.targetLanguage

        val adocFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .filter { file ->
                val relPath = file.relativeTo(sourceDir).path
                val dirsToCheck = generateSequence(relPath) { path ->
                    path.lastIndexOf('/').let { if (it > 0) path.substring(0, it) else null }
                }.toSet()
                excludes.none { it in dirsToCheck || relPath.startsWith("$it/") }
            }
            .toList()

        log.info("[translateBatch] {} ({}→{}) — {} files found", sourceDir.name, srcLang, tgtLang, adocFiles.size)

        val translated = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for ((idx, file) in adocFiles.withIndex()) {
            val relPath = file.relativeTo(sourceDir).path
            val outputFile = outputDir.resolve(relPath)
            log.info("[translateBatch] [{}/{}] {} -> {}", idx + 1, adocFiles.size, relPath, outputFile.path)
            try {
                val original = file.readText()
                val rendered = documentTranslator.translate(original, srcLang, tgtLang)
                outputFile.parentFile.mkdirs()
                outputFile.writeText(rendered)
                translated.add(relPath)
            } catch (e: Exception) {
                val msg = "$relPath: ${e.message}"
                errors.add(msg)
                log.warn("[translateBatch] ERROR: {}", msg)
            }
        }

        log.info("[translateBatch] done — {} translated, {} errors", translated.size, errors.size)
        return BatchTranslationResult(translated, errors)
    }
}