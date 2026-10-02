package document

import document.semantic.SemanticColumnSpec
import document.semantic.SemanticTableSchema
import document.semantic.TableSemanticsMode
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Per-schema builder used inside the `semantic { schema("<name>") { … } }` block
 * (EPIC DOC-SEMANTIC-TABLE, US-3, decision D9).
 *
 * The consumer declares the column → role mapping ; the plugin public repository
 * never knows a business word (decision D4, gate OSS-clean). A column targets a
 * raw column either by its zero-based index ([column]) or by its header text
 * ([column] with a `String`).
 */
class SemanticSchemaDsl(
    val name: String,
) {
    private val columns = mutableListOf<SemanticColumnSpec>()

    /** Declares a column addressed by its zero-based [index]. */
    fun column(index: Int, role: String, required: Boolean = false) {
        columns += SemanticColumnSpec.byIndex(index, role, required)
    }

    /** Declares a column addressed by its [header] text (case-insensitive). */
    fun column(header: String, role: String, required: Boolean = false) {
        columns += SemanticColumnSpec.byHeader(header, role, required)
    }

    internal fun build(): SemanticTableSchema = SemanticTableSchema(name = name, columns = columns.toList())
}

/**
 * Nested DSL block `semantic { }` (EPIC DOC-SEMANTIC-TABLE, US-3, decision D9).
 *
 * Concrete class with eagerly-initialised [Property]s so the Kotlin DSL resolves
 * `tableSemantics` and `schema("<name>") { … }` inside the block (pattern
 * [DocumentEnrichDsl] / [BookDsl]).
 *
 * ```
 * document {
 *     semantic {
 *         tableSemantics.set(TableSemanticsMode.LENIENT)   // OFF by default
 *         schema("planning") {
 *             column(0, role = "reference")
 *             column("Title", role = "title")
 *             column(2, role = "objective", required = true)
 *         }
 *     }
 * }
 * ```
 */
class SemanticDsl(
    val tableSemantics: Property<TableSemanticsMode>,
    val schemas: ListProperty<SemanticTableSchema>,
) {
    /**
     * Registers a named schema. Each invocation appends one schema to
     * [schemas] ; the annotation `[.semantic-<name>]` in the source selects it
     * (decision D3).
     */
    fun schema(name: String, action: Action<SemanticSchemaDsl>) {
        val builder = SemanticSchemaDsl(name)
        action.execute(builder)
        schemas.add(builder.build())
    }
}
