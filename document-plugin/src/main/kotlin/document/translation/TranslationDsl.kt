package document.translation

import org.gradle.api.provider.Property

class TranslationDsl(
    val sourceLanguage: Property<String>,
    val targetLanguage: Property<String>,
    val sourceFile: Property<String>,
    val outputFileName: Property<String>,
    val llmMode: Property<String>,
    val batchSourceDir: Property<String>,
    val batchOutputDir: Property<String>,
    val batchExcludePaths: Property<String>,
)