package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import document.translation.validation.ValidationMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * XF-FP3-1 — the translator is **stateless**: each pass returns its own
 * [TranslationTrace], so a reused instance never leaks the diagnostics of a
 * previous document into the next one.
 *
 * Regression: the translator used to own mutable `translationFailures` /
 * `tableValidationResults` / `plantUmlValidationResults` collections; reading
 * them required the caller to `clear()` first (one forgetting = leaked state,
 * one concurrent worker = cross-article contamination).
 */
class DocumentTranslatorStatelessnessTest {
    private val tableSource =
        """title=Test
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
| Valeur 1 | Valeur 2
|===
"""

    private val cleanSource =
        """title=Autre
date=2026-07-20
type=page
status=published
~~~~~~

== Section

Un paragraphe sans tableau.
"""

    private val corruptingService =
        object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted =
                    if (request.sourceText == "Valeur 1") {
                        "Valeur |=== broken [EN]"
                    } else {
                        request.sourceText + " [EN]"
                    }
                return TranslationResult.Success(corrupted)
            }
        }

    @Test
    fun `a second pass reports no validation result from the first one`() {
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.LENIENT)

        val first = translator.translateOutcome(tableSource, "fr", "en")
        val second = translator.translateOutcome(cleanSource, "fr", "en")

        assertEquals(1, first.trace.tableValidationResults.size, "the first pass must report its invalid table")
        assertTrue(
            second.trace.tableValidationResults.isEmpty(),
            "the second pass must not inherit the first pass' findings: ${second.trace.tableValidationResults}",
        )
    }

    @Test
    fun `a second pass reports no translation failure from the first one`() {
        val service = FailingService { it == "First paragraph." }
        val translator = DocumentTranslator(service)

        val first = translator.translateOutcome(sourceWithFailingParagraph(), "fr", "en")
        val second = translator.translateOutcome(cleanSource, "fr", "en")

        assertEquals(setOf("1"), first.trace.translationFailures)
        assertTrue(
            second.trace.translationFailures.isEmpty(),
            "the second pass must not inherit the first pass' failures: ${second.trace.translationFailures}",
        )
    }

    @Test
    fun `a failing nested admonition is attributed to its top-level block`() {
        val source =
            """= Intro

== Warning

[WARNING]
====
Second paragraph.
====
"""
        val service = FailingService { it == "Second paragraph." }
        val translator = DocumentTranslator(service)

        val outcome = translator.translateOutcome(source, "fr", "en")

        assertEquals(setOf("1"), outcome.trace.translationFailures)
        assertTrue(outcome.content.contains("Second paragraph."), "the silent fallback must still be rendered")
    }

    private fun sourceWithFailingParagraph(): String =
        """= Intro

== Heading One

First paragraph.

== Heading Two

Second paragraph.
"""

    private class FailingService(
        private val failFor: (String) -> Boolean,
    ) : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            if (failFor(request.sourceText)) {
                TranslationResult.Failure("LLM quota exceeded")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
    }
}
