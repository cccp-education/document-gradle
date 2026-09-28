package document.semantic

/**
 * Result of extracting one raw annotated table against the injected schemas
 * (EPIC DOC-SEMANTIC-TABLE, US-1, decisions D5/D8).
 *
 * Sealed on purpose : a raw annotated table either resolves to a declared schema
 * ([Extracted]) or is kept visible as an explicitly [Skipped] table — never a
 * silent drop (risk #6 mitigation, no `[.semantic-<name>]` ghost).
 */
sealed class SemanticExtraction {
    /** The [table] was projected against its declared schema. */
    data class Extracted(val table: SemanticTable) : SemanticExtraction()

    /**
     * The annotated table [name] had no declared schema.
     *
     * @param name the annotation name found in the source
     * @param reason a human-readable explanation (never blank)
     */
    data class Skipped(val name: String, val reason: String) : SemanticExtraction() {
        init {
            require(reason.isNotBlank()) { "a skipped table must carry a reason" }
        }
    }
}

/**
 * Pure orchestration of the semantic extraction (EPIC DOC-SEMANTIC-TABLE, US-1).
 *
 * [extract] pairs every [RawTable] (already filtered to annotated tables by the
 * reader port, decision D3) with the schema the consumer declared under the same
 * [name] (decision D4). The order of the results follows the raw table order, so
 * the export is deterministic. The domain is Gradle-free and free of I/O.
 */
object SemanticExtractor {

    /**
     * @param rawTables the annotated tables read from the AST, in document order
     * @param schemas the schemas declared by the consumer (matched by name)
     */
    fun extract(
        rawTables: List<RawTable>,
        schemas: List<SemanticTableSchema>,
    ): List<SemanticExtraction> {
        val byName = schemas.associateBy { it.name }
        return rawTables.map { raw ->
            val schema = byName[raw.name]
            if (schema == null) {
                SemanticExtraction.Skipped(
                    name = raw.name,
                    reason = "no schema declared for semantic table '${raw.name}'",
                )
            } else {
                SemanticExtraction.Extracted(SemanticTable.projection(raw, schema))
            }
        }
    }
}
