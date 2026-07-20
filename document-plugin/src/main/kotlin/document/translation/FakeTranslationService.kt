package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService

class FakeTranslationService(private val suffix: String = " [EN]") : TranslationService {
    override fun translate(request: TranslationRequest): TranslationResult {
        return TranslationResult.Success(request.sourceText + suffix)
    }
}
