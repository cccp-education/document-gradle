package document

import contracts.i18n.LanguageCatalog
import contracts.i18n.SupportedLanguage

/**
 * Bridge entre les contrats N0 i18n-contracts et le pipeline documentaire.
 *
 * Permet d'internationaliser les documents generes en selectionnant
 * une [SupportedLanguage] depuis la configuration du plugin.
 *
 * EPIC DOC-8 : consommation des contrats N0 (i18n, pipeline, session).
 * pipeline-contracts (MEM-2) pas encore publie — releaseNotesGenerate en TODO.
 */
data class DocumentI18nBridge(
    val language: SupportedLanguage = LanguageCatalog.findByCode("en") ?: SupportedLanguage("en", "English", "English", false, "en"),
    val messageBundlePath: String? = null,
) {

    fun resolveLocale(): String = language.localeTag

    fun isDefault(): Boolean = language.code == "en" && messageBundlePath == null
}