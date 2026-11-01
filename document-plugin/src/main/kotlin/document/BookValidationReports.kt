package document

import document.translation.TranslationTrace
import document.translation.validation.PlantUmlValidationReport
import document.translation.validation.TableValidationReport
import java.io.File

/**
 * Persists the diagnostics collected while translating a book (XF-FP3-2).
 *
 * `BookTranslator` used to **discard** the [TranslationTrace] of every page: the
 * table / PlantUML findings stopped at the per-page translator, so a translated
 * book could contain a broken table with no trace of it. The runner forwards the
 * merged trace here, and this object writes one report per concern, **per target
 * language** (a multi-language run must not overwrite the `fr` report with the
 * `en` one).
 *
 * Ink Economy Law: a clean trace writes **no** file — the absence of a report
 * means "nothing to report", never "the report step was skipped".
 *
 * The JSON schemas are the existing [TableValidationReport] /
 * [PlantUmlValidationReport] ones (the same files the batch translator writes),
 * so consumers parse one contract, not two.
 */
object BookValidationReports {
    /**
     * @param trace the merged diagnostics of every translated page
     * @param directory the book output directory
     * @param targetLanguage the language suffix of the produced files
     * @return the files actually written (empty when the trace is clean)
     */
    fun write(
        trace: TranslationTrace,
        directory: File,
        targetLanguage: String,
    ): List<File> {
        if (trace.tableValidationResults.isEmpty() && trace.plantUmlValidationResults.isEmpty()) {
            return emptyList()
        }
        directory.mkdirs()
        return buildList {
            if (trace.tableValidationResults.isNotEmpty()) {
                add(
                    write(
                        directory.resolve("book-$targetLanguage-table-validation-report.json"),
                        TableValidationReport.fromResults(trace.tableValidationResults).toJson(),
                    ),
                )
            }
            if (trace.plantUmlValidationResults.isNotEmpty()) {
                add(
                    write(
                        directory.resolve("book-$targetLanguage-plantuml-validation-report.json"),
                        PlantUmlValidationReport.fromResults(trace.plantUmlValidationResults).toJson(),
                    ),
                )
            }
        }
    }

    private fun write(
        file: File,
        content: String,
    ): File = file.apply { writeText(content) }
}
