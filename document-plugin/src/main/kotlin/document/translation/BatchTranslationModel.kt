package document.translation

import java.io.File

data class BatchTranslationRequest(
    val sourceDir: File,
    val outputDir: File,
    val sourceLanguage: String,
    val targetLanguage: String,
    val excludeRelativePaths: Set<String> = emptySet(),
)

data class BatchTranslationResult(
    val translatedFiles: List<String>,
    val errors: List<String>,
) {
    val count: Int get() = translatedFiles.size
    val success: Boolean get() = errors.isEmpty()
    val hasErrors: Boolean get() = errors.isNotEmpty()
}