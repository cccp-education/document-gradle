package document.semantic

/**
 * A single column of a [SemanticTableSchema] (EPIC DOC-SEMANTIC-TABLE, US-1).
 *
 * A column is deliberately *abstract* : it targets a raw table column either by
 * its zero-based [index] or by its [header] text (case-insensitive), and names
 * an opaque [role]. The plugin public repository never knows a business word —
 * the consumer declares its own schema (decision D4, gate OSS-clean).
 *
 * Exactly one of [index] / [header] must be set. A column marked [required]
 * whose projected cell is blank yields a finding (decision D8).
 */
data class SemanticColumnSpec(
    val index: Int? = null,
    val header: String? = null,
    val role: String,
    val required: Boolean = false,
) : java.io.Serializable {
    init {
        require(role.isNotBlank()) { "a column role must not be blank" }
        require(index != null || header?.isNotBlank() == true) {
            "a column must target either a zero-based index or a non-blank header"
        }
        require(header == null || header.isNotBlank()) { "a column header must not be blank" }
        require(index == null || index >= 0) { "a column index must be >= 0 but was $index" }
    }

    companion object {
        /** Builds a column addressed by its zero-based [index]. */
        fun byIndex(index: Int, role: String, required: Boolean = false): SemanticColumnSpec =
            SemanticColumnSpec(index = index, role = role, required = required)

        /** Builds a column addressed by its [header] text (case-insensitive). */
        fun byHeader(header: String, role: String, required: Boolean = false): SemanticColumnSpec =
            SemanticColumnSpec(header = header, role = role, required = required)
    }
}

/**
 * A named schema the consumer declared through the DSL (EPIC DOC-SEMANTIC-TABLE,
 * US-1, decision D4).
 *
 * The schema pairs an abstract [name] (matched against the AsciiDoc annotation
 * `[.semantic-<name>]`, decision D3) with the [columns] to project. Column
 * roles are unique inside a schema, so a projected record is unambiguous.
 */
data class SemanticTableSchema(
    val name: String,
    val columns: List<SemanticColumnSpec>,
) : java.io.Serializable {
    init {
        require(name.isNotBlank()) { "a schema name must not be blank" }
        require(columns.isNotEmpty()) { "a schema must declare at least one column" }
        val duplicated = columns.map { it.role }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicated.isEmpty()) { "a schema must not declare a duplicated role: ${duplicated.sorted()}" }
    }
}
