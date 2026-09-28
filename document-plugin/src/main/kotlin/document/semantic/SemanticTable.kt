package document.semantic

/**
 * A raw annotated table extracted from the AsciidoctorJ AST by the
 * `SemanticTableReader` port (EPIC DOC-SEMANTIC-TABLE, US-1/US-2).
 *
 * This is the *Gradle-free and AsciidoctorJ-free* representation exchanged
 * between the adapter (which owns `org.asciidoctor.ast.*`) and the pure domain :
 * a [name] (from the `[.semantic-<name>]` annotation, decision D3), the optional
 * [header] row, and the body [rows] as raw cell texts. The domain never sees an
 * AST node.
 */
data class RawTable(
    val name: String,
    val header: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a raw table name must not be blank" }
    }
}

/**
 * One projected record of a [SemanticTable] : a mapping from an abstract `role`
 * to the raw cell text (EPIC DOC-SEMANTIC-TABLE, US-1).
 *
 * The mapping keeps the schema declaration order, so the serialised record is
 * deterministic. A missing or whitespace-only cell counts as [blank]
 * (decision D8), which the required-role rule consumes.
 */
data class SemanticRow(
    val values: Map<String, String>,
) {
    init {
        require(values.keys.all { it.isNotBlank() }) { "a projected role key must not be blank" }
    }

    /** The value projected for [role], or `null` when the role is absent. */
    operator fun get(role: String): String? = values[role]

    /** The declared role keys, in schema order. */
    val roles: List<String> get() = values.keys.toList()

    /** `true` when [role] is absent or carries only whitespace. */
    fun isBlank(role: String): Boolean = values[role]?.isBlank() ?: true
}

/**
 * A finding raised by the semantic projection : a `required` role whose cell is
 * blank (EPIC DOC-SEMANTIC-TABLE, US-1, decision D8).
 *
 * The domain *records* findings; it never throws. The severity
 * (STRICT/LENIENT/OFF) is applied by the task layer (US-4), symmetrically to
 * `document.xref` / `document.validation`.
 */
data class SemanticFinding(
    val rowIndex: Int,
    val role: String,
    val message: String,
)

/**
 * A projected semantic table : the records derived from a [RawTable] and an
 * injected [SemanticTableSchema], plus the [findings] raised by the required-role
 * rule (EPIC DOC-SEMANTIC-TABLE, US-1, decisions D4/D5/D8).
 *
 * [projection] is a pure deterministic function of its inputs — no I/O, no
 * Gradle, no AsciidoctorJ — so it is fully unit-testable in isolation.
 */
data class SemanticTable(
    val name: String,
    val raw: RawTable,
    val records: List<SemanticRow>,
    val findings: List<SemanticFinding>,
) {
    companion object {
        /**
         * Projects [raw] onto the injected [schema] :
         * - each schema column is resolved by its zero-based `index` (falling
         *   back to the header lookup), then mapped to its `role` ;
         * - a cell absent from a ragged row projects as `""` (never a crash) ;
         * - a column whose header is not found in [raw] projects as `""` ;
         * - columns of [raw] not declared in [schema] are ignored (Économie
         *   d'Encre) ;
         * - a `required` role with a blank cell yields a finding, ordered by row
         *   then schema column order.
         */
        fun projection(raw: RawTable, schema: SemanticTableSchema): SemanticTable {
            val records = raw.rows.map { cells -> projectRow(raw, cells, schema) }
            val findings = records.flatMapIndexed { rowIndex, record ->
                schema.columns
                    .filter { it.required && record.isBlank(it.role) }
                    .map { SemanticFinding(rowIndex, it.role, "required role '${it.role}' is blank") }
            }
            return SemanticTable(name = schema.name, raw = raw, records = records, findings = findings)
        }

        private fun projectRow(
            raw: RawTable,
            cells: List<String>,
            schema: SemanticTableSchema,
        ): SemanticRow {
            val values = LinkedHashMap<String, String>(schema.columns.size)
            schema.columns.forEach { column ->
                val index = when {
                    column.index != null -> column.index
                    column.header != null ->
                        raw.header.indexOfFirst { it.equals(column.header, ignoreCase = true) }
                    else -> -1
                }
                val value = cells.getOrNull(index) ?: ""
                values[column.role] = value
            }
            return SemanticRow(values)
        }
    }
}
