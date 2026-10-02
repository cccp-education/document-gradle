package document.semantic

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/**
 * One extracted table in the `table-semantics.json` export
 * (EPIC DOC-SEMANTIC-TABLE, US-3, decision D7).
 *
 * [records] is the list of projected records (role → value); the map keeps the
 * schema declaration order so the serialised JSON is deterministic.
 */
data class SemanticExtractedTableReport(
    val name: String,
    val records: List<Map<String, String>>,
    val findings: List<SemanticFindingReport>,
)

/**
 * One annotation present in the source but without a declared schema
 * (EPIC DOC-SEMANTIC-TABLE, US-3) — kept visible, never a silent drop
 * (risk #6 mitigation).
 */
data class SemanticSkippedReport(
    val name: String,
    val reason: String,
)

/**
 * One required-role finding in the export (EPIC DOC-SEMANTIC-TABLE, US-1/D8),
 * decorated with the source [table] name for a self-contained report.
 */
data class SemanticFindingReport(
    val table: String,
    val rowIndex: Int,
    val role: String,
    val message: String,
)

/**
 * JSON report of the semantic table lifting (`table-semantics.json`, EPIC
 * DOC-SEMANTIC-TABLE, US-3, decision D7).
 *
 * Like [document.xref.XrefValidationReport] the report is a pure serialisable
 * value: the domain produces it, the task persists it — no side effect inside
 * the report. The [mode] is recorded so a consumer can tell a `LENIENT` run
 * (findings tolerated) from a `STRICT` one (report written before the fail-fast
 * [org.gradle.api.GradleException]).
 *
 * Ink Economy Law: the report never re-reads the AST — it is built from the
 * [SemanticExtraction] list the pure domain already produced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableSemanticsReport(
    val mode: String,
    val schemas: List<String>,
    val tables: List<SemanticExtractedTableReport>,
    val skipped: List<SemanticSkippedReport>,
    val findings: List<SemanticFindingReport>,
) {
    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }

    companion object {
        /**
         * Builds the report from the declared [schemas] and the [extractions]
         * returned by [SemanticExtractor], preserving the document order of the
         * extracted/skipped tables (deterministic export).
         */
        fun fromExtractions(
            mode: TableSemanticsMode,
            schemas: List<SemanticTableSchema>,
            extractions: List<SemanticExtraction>,
        ): TableSemanticsReport {
            val tables = extractions.filterIsInstance<SemanticExtraction.Extracted>().map { extraction ->
                SemanticExtractedTableReport(
                    name = extraction.table.name,
                    records = extraction.table.records.map { it.values },
                    findings = extraction.table.findings.map { finding ->
                        SemanticFindingReport(
                            table = extraction.table.name,
                            rowIndex = finding.rowIndex,
                            role = finding.role,
                            message = finding.message,
                        )
                    },
                )
            }
            val skipped = extractions.filterIsInstance<SemanticExtraction.Skipped>().map {
                SemanticSkippedReport(name = it.name, reason = it.reason)
            }
            val findings = tables.flatMap { it.findings }
            return TableSemanticsReport(
                mode = mode.name,
                schemas = schemas.map { it.name },
                tables = tables,
                skipped = skipped,
                findings = findings,
            )
        }

        /** The report of a run that lifted no annotated table. */
        fun empty(mode: TableSemanticsMode): TableSemanticsReport = TableSemanticsReport(
            mode = mode.name,
            schemas = emptyList(),
            tables = emptyList(),
            skipped = emptyList(),
            findings = emptyList(),
        )
    }
}
