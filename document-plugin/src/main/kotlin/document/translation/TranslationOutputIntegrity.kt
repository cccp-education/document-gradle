package document.translation

import document.translation.delta.BlockChecksum

/**
 * DOC-I18N-OUTPUT DOC-OUT-2 — structural guard on the translated artefact.
 *
 * Translation replaces text, never structure: the rendered article must carry
 * exactly the same number of checksum-relevant blocks as the source. The
 * measured cheroliv.com defects (`duplicated-content`, up to +123 % on EN 0108
 * and 9 files in `hi`) came from a block being re-inserted more than once.
 * Comparing the checksum-relevant block counts (the same projection the delta
 * store uses) detects duplication and truncation independently of the language.
 *
 * Pure domain: the caller supplies the source and rendered blocks; the verdict
 * is unit-testable without a Gradle build or an LLM.
 */
object TranslationOutputIntegrity {
    sealed interface Verdict {
        data class Ok(
            val blocks: Int,
        ) : Verdict

        data class Duplicated(
            val expected: Int,
            val actual: Int,
        ) : Verdict

        data class Truncated(
            val expected: Int,
            val actual: Int,
        ) : Verdict
    }

    fun verdict(
        sourceBlocks: List<PivotBlock>,
        renderedBlocks: List<PivotBlock>,
    ): Verdict {
        val expected = BlockChecksum.computeForBlocks(sourceBlocks).size
        val actual = BlockChecksum.computeForBlocks(renderedBlocks).size
        return when {
            actual > expected -> Verdict.Duplicated(expected, actual)
            actual < expected -> Verdict.Truncated(expected, actual)
            else -> Verdict.Ok(expected)
        }
    }

    /** Fails the translation run before the duplicated artefact is written. */
    fun requireNoDuplication(
        sourceBlocks: List<PivotBlock>,
        renderedBlocks: List<PivotBlock>,
        articleLabel: String,
    ) {
        val verdict = verdict(sourceBlocks, renderedBlocks)
        if (verdict is Verdict.Duplicated) {
            throw TranslationException(
                "Translation of '$articleLabel' duplicated blocks: " +
                    "${verdict.actual} rendered vs ${verdict.expected} expected " +
                    "(block identity is (article, index, hash); a block must be rendered once)",
            )
        }
    }
}
