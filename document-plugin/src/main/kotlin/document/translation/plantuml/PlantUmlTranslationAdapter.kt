package document.translation.plantuml

import document.translation.PivotBlock
import document.translation.validation.PlantUmlSyntaxValidator
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.ValidationMode
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.slf4j.LoggerFactory

class PlantUmlTranslationAdapter(
    private val translationService: TranslationService,
    private val classifier: PlantUmlClassifier = PlantUmlClassifier(),
    private val plantUmlValidator: PlantUmlSyntaxValidator = PlantUmlSyntaxValidator.create(),
    private val plantUmlValidationMode: ValidationMode = ValidationMode.LENIENT,
) {

    private val log = LoggerFactory.getLogger(PlantUmlTranslationAdapter::class.java)

    private val borrowedVocabulary = setOf("REAC", "AFNOR", "DC", "TS", "RNCP", "CP", "ECF")

    private val placeholderOpen = "\uE000"
    private val placeholderClose = "\uE001"

    val plantUmlValidationResults: MutableList<PlantUmlValidationResult.Invalid> = mutableListOf()

    fun translate(
        block: PivotBlock.Source,
        sourceLanguage: String,
        targetLanguage: String,
        articleTitle: String = "",
        blockIndex: Int = 0,
    ): PivotBlock.Source {
        if (block.language != "plantuml") return block
        val strategy = classifier.classify(PlantUmlBlock(block.content))
        val result = when (strategy) {
            PlantUmlStrategy.PreserveTechnical -> block
            PlantUmlStrategy.TranslateLabels -> translateLabels(block, sourceLanguage, targetLanguage, preserveVocabulary = false)
            PlantUmlStrategy.BorrowVocabulary -> translateLabels(block, sourceLanguage, targetLanguage, preserveVocabulary = true)
        }
        if (strategy != PlantUmlStrategy.PreserveTechnical) {
            validateTranslatedPlantUml(result.content, articleTitle, blockIndex, strategy.toString())
        }
        return result
    }

    private fun validateTranslatedPlantUml(
        plantumlCode: String,
        articleTitle: String,
        blockIndex: Int,
        strategy: String,
    ) {
        if (plantUmlValidationMode == ValidationMode.OFF) return
        val validationResult = plantUmlValidator.validate(plantumlCode, articleTitle, blockIndex, strategy)
        if (validationResult is PlantUmlValidationResult.Invalid) {
            plantUmlValidationResults.add(validationResult)
            val msg = "PlantUML validation failed in article '$articleTitle' block #$blockIndex (strategy=$strategy): ${validationResult.reason}"
            when (plantUmlValidationMode) {
                ValidationMode.STRICT -> throw document.translation.TranslationException(msg)
                ValidationMode.LENIENT -> log.warn(msg)
                ValidationMode.OFF -> {}
            }
        }
    }

    private fun translateLabels(
        block: PivotBlock.Source,
        sourceLanguage: String,
        targetLanguage: String,
        preserveVocabulary: Boolean
    ): PivotBlock.Source {
        var content = block.content
        val placeholders = mutableMapOf<String, String>()
        if (preserveVocabulary) {
            borrowedVocabulary.forEachIndexed { idx, term ->
                val token = "$placeholderOpen$idx$placeholderClose"
                placeholders[token] = term
                content = content.replace("\"$term\"", "\"$token\"")
            }
        }
        val labels = PlantUmlBlock(content).labels()
        var translated = content
        for (label in labels.distinct()) {
            val replacement = doTranslate(label, sourceLanguage, targetLanguage) ?: continue
            translated = translated.replace("\"$label\"", "\"$replacement\"")
        }
        if (preserveVocabulary) {
            for ((token, term) in placeholders) {
                translated = translated.replace("\"$token\"", "\"$term\"")
            }
        }
        return block.copy(content = translated)
    }

    private fun doTranslate(text: String, sourceLanguage: String, targetLanguage: String): String? {
        if (text.isBlank()) return null
        val request = TranslationRequest(text, sourceLanguage, targetLanguage)
        return when (val result = translationService.translate(request)) {
            is TranslationResult.Success -> result.translatedText
            is TranslationResult.Failure -> null
        }
    }
}
