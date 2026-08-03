package document.translation.validation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService

class CorruptingTranslationService(
    private val delegate: TranslationService = document.translation.FakeTranslationService(" [EN]"),
    private val corruptions: MutableMap<String, String> = mutableMapOf(),
) : TranslationService {
    fun corrupt(sourceText: String, corruptedText: String) {
        corruptions[sourceText] = corruptedText
    }

    override fun translate(request: TranslationRequest): TranslationResult {
        return if (corruptions.containsKey(request.sourceText)) {
            TranslationResult.Success(corruptions[request.sourceText]!!)
        } else {
            delegate.translate(request)
        }
    }
}
