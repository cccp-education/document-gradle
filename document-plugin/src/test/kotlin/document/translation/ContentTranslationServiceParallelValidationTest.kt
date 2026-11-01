package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import document.translation.validation.ValidationMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

/**
 * XF-FP3-1 — findings produced by the **parallel** translation path.
 *
 * Regression: the translator used to own a mutable `tableValidationResults`
 * list; `translateSingleFile` ran from several `Dispatchers.IO` coroutines and
 * appended to that shared `LinkedHashSet`-backed list without synchronisation.
 * The trace is now returned per pass and merged into a `ConcurrentLinkedQueue`
 * owned by the service, so no finding can be lost.
 */
class ContentTranslationServiceParallelValidationTest {
    @TempDir
    lateinit var tempDir: File

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

    private fun writeArticle(
        dir: File,
        index: Int,
    ): File =
        dir.resolve("doc-$index.adoc").apply {
            writeText(
                """title=Doc $index
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
""",
            )
        }

    @Test
    fun `parallel translation records one finding per file`() {
        val files = (1..8).map { writeArticle(tempDir, it) }
        val service =
            ContentTranslationService(
                corruptingService,
                parallelism = 4,
                tableValidationMode = ValidationMode.LENIENT,
            )

        val result = service.translateFiles(files, tempDir, "fr", "en")
        val findings = service.drainTableValidationResults()

        assertEquals(8, result.filesTranslated.size, "every file must be translated")
        assertEquals(
            files.map { "Doc ${it.name.substringAfter("doc-").substringBefore(".adoc")}" }.toSet(),
            findings.map { it.articleTitle }.toSet(),
            "each file must contribute its own finding, none lost to the shared state race",
        )
        assertEquals(8, findings.size, "one invalid table per file: $findings")
    }
}
