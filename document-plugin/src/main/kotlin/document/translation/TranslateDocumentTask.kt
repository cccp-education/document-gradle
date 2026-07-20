package document.translation

import contracts.i18n.TranslationService
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

@DisableCachingByDefault(because = "Translation output depends on LLM which is non-deterministic")
abstract class TranslateDocumentTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val sourceLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val targetLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val llmMode: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        description = "Translates an AsciiDoc document from source language to target language via LLM."
        sourceLanguage.convention("fr")
        targetLanguage.convention("en")
        llmMode.convention("ollama")
    }

    @TaskAction
    fun translate() {
        val logger = LoggerFactory.getLogger(TranslateDocumentTask::class.java)
        val source = sourceFile.get().asFile
        val output = outputFile.get().asFile
        val srcLang = sourceLanguage.get()
        val tgtLang = targetLanguage.get()
        val mode = llmMode.get()

        val translationService: TranslationService = when (mode.lowercase()) {
            "fake" -> FakeTranslationService(" [${tgtLang.uppercase()}]")
            "ollama" -> OllamaTranslationAdapter()
            else -> throw IllegalArgumentException("Unknown llmMode: '$mode' — expected 'ollama' or 'fake'")
        }

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val service = ContentTranslationService(translationService, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, srcLang, tgtLang)
        val rendered = renderer.render(translated)

        output.parentFile.mkdirs()
        output.writeText(rendered)
        logger.info("translateDocument — {} ({}→{}) -> {}", source.name, srcLang, tgtLang, output.absolutePath)
    }
}
