package document.translation

import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableValidationResult

/**
 * Immutable diagnostics produced by **one** translation pass (XF-FP3-1).
 *
 * Why a value instead of mutable fields: [DocumentTranslator] used to publish
 * `translationFailures` / `tableValidationResults` / `plantUmlValidationResults`
 * as mutable collections owned by the instance — the state *escaped* the
 * translator, callers had to `clear()` it, and a reused instance leaked results
 * from a previous article into the next. The trace is now **returned** with the
 * translation, so a pass has no ambient state and two passes cannot interfere.
 *
 * @property translationFailures indices (as `String`, matching `BlockChecksum`
 *   keys) of the top-level blocks whose LLM call failed — the caller stores them
 *   as `PENDING` so the next delta re-attempts them.
 * @property tableValidationResults invalid table syntax found in the produced document.
 * @property plantUmlValidationResults invalid PlantUML syntax found in the produced document.
 */
internal data class TranslationTrace(
    val translationFailures: Set<String> = emptySet(),
    val tableValidationResults: List<TableValidationResult.Invalid> = emptyList(),
    val plantUmlValidationResults: List<PlantUmlValidationResult.Invalid> = emptyList(),
) {
    fun merge(other: TranslationTrace): TranslationTrace =
        TranslationTrace(
            translationFailures = translationFailures + other.translationFailures,
            tableValidationResults = tableValidationResults + other.tableValidationResults,
            plantUmlValidationResults = plantUmlValidationResults + other.plantUmlValidationResults,
        )

    companion object {
        val EMPTY = TranslationTrace()
    }
}

/** A rendered document and the diagnostics of the pass that produced it. */
internal data class DocumentTranslation(
    val content: String,
    val trace: TranslationTrace = TranslationTrace.EMPTY,
)

/** A translated pivot article and the diagnostics of the pass that produced it. */
internal data class ArticleTranslation(
    val article: PivotArticle,
    val trace: TranslationTrace = TranslationTrace.EMPTY,
)

/** A translated top-level block list and the diagnostics of the pass that produced it. */
internal data class BlocksTranslation(
    val blocks: List<PivotBlock>,
    val trace: TranslationTrace = TranslationTrace.EMPTY,
)
