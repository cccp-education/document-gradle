package document.translation.plan

import document.translation.plantuml.PlantUmlStrategy
import contracts.i18n.LanguageCatalog

data class SiteTranslationPlan(
    val siteName: String,
    val sourceLanguage: String,
    val targetLanguages: Set<String>,
    val defaultPlantUmlStrategy: PlantUmlStrategy = PlantUmlStrategy.PreserveTechnical
) {
    init {
        require(siteName.isNotBlank()) {
            "Site name (siteName) is required for the translation plan."
        }
        require(sourceLanguage.isNotBlank()) {
            "Source language (sourceLanguage) is required."
        }
        require(targetLanguages.isNotEmpty()) {
            "At least one target language is required."
        }
        require(sourceLanguage in LanguageCatalog.supportedCodes()) {
            "Source language '$sourceLanguage' not supported. Use: ${LanguageCatalog.supportedCodes().joinToString()}."
        }
        targetLanguages.forEach { lang ->
            require(lang in LanguageCatalog.supportedCodes()) {
                "Target language '$lang' not supported. Use: ${LanguageCatalog.supportedCodes().joinToString()}."
            }
        }
        require(sourceLanguage !in targetLanguages) {
            "Source language '$sourceLanguage' cannot be a target language."
        }
    }

    fun rtlTargets(): Set<String> =
        targetLanguages.filter { LanguageCatalog.findByCode(it)?.rtl == true }.toSet()

    fun ltrTargets(): Set<String> = targetLanguages - rtlTargets()

    fun missingLanguages(existing: Set<String>): Set<String> = targetLanguages - existing

    fun isComplete(existing: Set<String>): Boolean = missingLanguages(existing).isEmpty()

    fun withTargetLanguages(newTargets: Set<String>): SiteTranslationPlan =
        copy(targetLanguages = newTargets)
}
