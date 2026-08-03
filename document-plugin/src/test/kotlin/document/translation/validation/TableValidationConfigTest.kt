package document.translation.validation

import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import document.translation.TranslationException
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TableValidationConfigTest {

    @Test
    fun `LENIENT mode warns but does not throw`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 1") {
                    "Valeur |=== broken [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.LENIENT)

        val source = """title=Test
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

        val result = translator.translate(source, "fr", "en")

        assertContains(result, "Valeur |=== broken [EN]")
        assertContains(result, "Colonne A [EN]")
    }

    @Test
    fun `STRICT mode throws TranslationException on invalid table`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 1") {
                    "Valeur |=== broken [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.STRICT)

        val source = """title=Test
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

        assertFailsWith<TranslationException> {
            translator.translate(source, "fr", "en")
        }
    }

    @Test
    fun `OFF mode skips validation entirely`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 1") {
                    "Valeur |=== broken [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.OFF)

        val source = """title=Test
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

        val result = translator.translate(source, "fr", "en")

        assertContains(result, "Valeur |=== broken [EN]")
        assertContains(result, "Colonne A [EN]")
        assertTrue(translator.tableValidationResults.isEmpty())
    }

    @Test
    fun `default mode is LENIENT`() {
        val translator = DocumentTranslator(FakeTranslationService(" [EN]"))

        val source = """title=Test
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

        val result = translator.translate(source, "fr", "en")

        assertContains(result, "Colonne A [EN]")
        assertContains(result, "Colonne B [EN]")
    }

    @Test
    fun `STRICT mode throws on column count mismatch`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 1") {
                    "Valeur |=== 1 [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.STRICT)

        val source = """title=Test
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

        assertFailsWith<TranslationException> {
            translator.translate(source, "fr", "en")
        }
    }

    @Test
    fun `STRICT mode does not throw on valid table`() {
        val translator = DocumentTranslator(FakeTranslationService(" [EN]"), tableValidationMode = ValidationMode.STRICT)

        val source = """title=Test
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

        val result = translator.translate(source, "fr", "en")

        assertContains(result, "Colonne A [EN]")
        assertContains(result, "Colonne B [EN]")
    }

    @Test
    fun `tableValidationResults accumulates invalid results in LENIENT mode`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 1") {
                    "Valeur |=== broken [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val translator = DocumentTranslator(corruptingService, tableValidationMode = ValidationMode.LENIENT)

        val source = """title=Test
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

        translator.translate(source, "fr", "en")

        assertTrue(translator.tableValidationResults.isNotEmpty())
        assertTrue(translator.tableValidationResults.any { it.reason.contains("|===") })
    }
}
