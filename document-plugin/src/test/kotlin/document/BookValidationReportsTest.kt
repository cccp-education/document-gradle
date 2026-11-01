package document

import com.fasterxml.jackson.databind.ObjectMapper
import document.translation.TranslationTrace
import document.translation.validation.PlantUmlValidationResult
import document.translation.validation.TableValidationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * XF-FP3-2 — the diagnostics of a translated book must not be thrown away.
 *
 * `BookTranslator` used to discard the `TranslationTrace` of every page (the
 * traceable findings stopped at [document.translation.DocumentTranslator]).
 * [BookValidationReports] persists them per target language, next to the book,
 * and **only when there is something to report** (Ink Economy Law: a clean book
 * writes no file).
 */
class BookValidationReportsTest {
    @TempDir
    lateinit var tempDir: File

    private val invalidTable =
        TableValidationResult.Invalid(
            articleTitle = "Chapter One",
            tableIndex = 0,
            reason = "cell [0,0] contains table delimiter '|==='",
        )

    private val invalidPlantUml =
        PlantUmlValidationResult.Invalid(
            articleTitle = "Chapter One",
            blockIndex = 0,
            strategy = "n0-port",
            reason = "unbalanced @startuml",
        )

    @Test
    fun `table findings are written per language`() {
        val written =
            BookValidationReports.write(
                trace = TranslationTrace(tableValidationResults = listOf(invalidTable)),
                directory = tempDir,
                targetLanguage = "en",
            )

        val report = tempDir.resolve("book-en-table-validation-report.json")
        assertTrue(written.contains(report), "the report file must be returned: $written")
        assertTrue(report.isFile, "expected ${report.name}")
        val entries = ObjectMapper().readTree(report.readText()).get("entries")
        assertEquals(1, entries.size(), "one finding must be reported: $entries")
        assertTrue(report.readText().contains("|==="))
    }

    @Test
    fun `plantuml findings are written per language`() {
        BookValidationReports.write(
            trace = TranslationTrace(plantUmlValidationResults = listOf(invalidPlantUml)),
            directory = tempDir,
            targetLanguage = "ar",
        )

        val report = tempDir.resolve("book-ar-plantuml-validation-report.json")
        assertTrue(report.isFile, "expected ${report.name}")
        assertTrue(report.readText().contains("unbalanced @startuml"))
    }

    @Test
    fun `a clean trace writes no file`() {
        val written = BookValidationReports.write(TranslationTrace.EMPTY, tempDir, "en")

        assertTrue(written.isEmpty(), "no finding -> no report (Ink Economy): $written")
        assertEquals(0, tempDir.listFiles()!!.size, "the directory must stay untouched")
    }
}
