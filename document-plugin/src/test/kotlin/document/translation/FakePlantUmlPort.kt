package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlClassifier
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest

/**
 * Test-only N0 [PlantUmlTranslationPort] fake (US-4 PLT-DIAGRAM-OWNERSHIP).
 *
 * document no longer owns PlantUML translation; in production the port is
 * implemented by the plantuml borough and injected by bakery. Tests need a
 * deterministic in-JVM port: this one mirrors the historical document behaviour
 * (translate the quoted labels through a [TranslationService]; PRESERVE keeps
 * the block) so the translation-pipeline tests stay meaningful.
 */
class FakePlantUmlPort(
    private val translationService: TranslationService,
    private val classifier: PlantUmlClassifier = PlantUmlClassifier(),
) : PlantUmlTranslationPort {

    override fun translate(request: PlantUmlTranslationRequest): PlantUmlTranslationOutcome {
        val block = request.block
        val strategy = classifier.classify(block)
        if (strategy == PlantUmlStrategy.PRESERVE) {
            return PlantUmlTranslationOutcome.Preserved("PRESERVE")
        }
        var raw = block.raw
        var changed = false
        for (label in block.labels()) {
            val result = translationService.translate(
                TranslationRequest(label, request.sourceLanguage, request.targetLanguage),
            )
            val translated = when (result) {
                is TranslationResult.Success -> result.translatedText
                is TranslationResult.Failure -> continue
            }
            if (translated == label) continue
            val escaped = translated.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
            val replaced = raw.replace("\"$label\"", "\"$escaped\"")
            if (replaced != raw) {
                raw = replaced
                changed = true
            }
        }
        return if (changed) {
            PlantUmlTranslationOutcome.Translated(block.copy(raw = raw))
        } else {
            PlantUmlTranslationOutcome.Preserved("no label changed")
        }
    }
}
