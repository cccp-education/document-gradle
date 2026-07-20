package document.translation

import org.gradle.api.provider.Property

class TranslationDsl(
    val sourceLanguage: Property<String>,
    val targetLanguage: Property<String>,
    val sourceFile: Property<String>,
    val outputFileName: Property<String>,
    val llmMode: Property<String>,
)
