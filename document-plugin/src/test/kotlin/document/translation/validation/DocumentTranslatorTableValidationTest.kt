package document.translation.validation

import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentTranslatorTableValidationTest {

    private val fakeService = FakeTranslationService(" [EN]")
    private val translator = DocumentTranslator(fakeService)

    @Test
    fun `valid table after translation passes validation silently`() {
        val source = """title=Valid Table
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

        assertTrue(result.contains("Colonne A [EN]"))
        assertTrue(result.contains("Colonne B [EN]"))
        assertTrue(result.contains("Valeur 1 [EN]"))
        assertTrue(result.contains("Valeur 2 [EN]"))
    }

    @Test
    fun `table with corrupted delimiter after translation is detected`() {
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
        val corruptingTranslator = DocumentTranslator(corruptingService)

        val source = """title=Corrupted Table
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

        val result = corruptingTranslator.translate(source, "fr", "en")

        assertTrue(result.contains("Valeur |=== broken [EN]"))
        assertTrue(result.contains("Colonne A [EN]"))
    }

    @Test
    fun `table with column mismatch after translation is detected`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Valeur 2") {
                    "Valeur 2 [EN] | Extra Column [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val corruptingTranslator = DocumentTranslator(corruptingService)

        val source = """title=Mismatch Table
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

        val result = corruptingTranslator.translate(source, "fr", "en")

        assertTrue(result.contains("Valeur 1 [EN]"))
        assertTrue(result.contains("Valeur 2 [EN]"))
    }

    @Test
    fun `multiple tables get correct indices`() {
        val source = """title=Multi Tables
date=2026-07-20
type=page
status=published
~~~~~~

== Section 1

[cols="1,1"]
|===
| Colonne Alpha | Colonne Beta
| Valeur Un | Valeur Deux
|===

== Section 2

[cols="1,1"]
|===
| Colonne Gamma | Colonne Delta
| Valeur Trois | Valeur Quatre
|===
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Colonne Alpha [EN]"))
        assertTrue(result.contains("Colonne Beta [EN]"))
        assertTrue(result.contains("Colonne Gamma [EN]"))
        assertTrue(result.contains("Colonne Delta [EN]"))
    }

    @Test
    fun `non-table blocks are unaffected by validation`() {
        val source = """title=No Tables
date=2026-07-20
type=page
status=published
~~~~~~

== Introduction

Ceci est un paragraphe.

. Item un
. Item deux

[source,java]
----
public class Hello {}
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Ceci est un paragraphe. [EN]"))
        assertTrue(result.contains("Item un [EN]"))
        assertTrue(result.contains("public class Hello {}"))
    }

    @Test
    fun `empty table passes validation`() {
        val source = """title=Empty Table
date=2026-07-20
type=page
status=published
~~~~~~

== Data

|===
|===
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("|==="))
    }

    @Test
    fun `table without cols passes validation`() {
        val source = """title=No Cols
date=2026-07-20
type=page
status=published
~~~~~~

== Data

|===
| Colonne Alpha | Colonne Beta
| Valeur Un | Valeur Deux
|===
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Colonne Alpha [EN]"))
        assertTrue(result.contains("Colonne Beta [EN]"))
    }

    @Test
    fun `table with header only passes validation`() {
        val source = """title=Header Only
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
|===
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Colonne A [EN]"))
        assertTrue(result.contains("Colonne B [EN]"))
    }

    @Test
    fun `table with delimiter in header cell after translation is detected`() {
        val corruptingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                val corrupted = if (request.sourceText == "Colonne A") {
                    "Colonne |=== A [EN]"
                } else {
                    request.sourceText + " [EN]"
                }
                return TranslationResult.Success(corrupted)
            }
        }
        val corruptingTranslator = DocumentTranslator(corruptingService)

        val source = """title=Header Corruption
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

        val result = corruptingTranslator.translate(source, "fr", "en")

        assertTrue(result.contains("Colonne |=== A [EN]"))
        assertTrue(result.contains("Valeur 1 [EN]"))
    }
}
