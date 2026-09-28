package document.semantic

import document.AsciidoctorHolder
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.Cell
import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.ast.Table
import java.io.File

/**
 * Adapter bridging the *real* AsciidoctorJ object model to the Gradle-free
 * [SemanticTableReader] port (EPIC DOC-SEMANTIC-TABLE, US-2, decision D6).
 *
 * The domain never depends on `org.asciidoctor.*` (dependency inversion, patron
 * `EpubCheckRunner`/`PdfValidator`) ; this adapter owns the AST access. It reuses
 * [AsciidoctorHolder] so the JRuby runtime is started once and shared with the
 * conversions (DOC-CR3-1), and it applies the same [safeMode] as the conversion
 * (decision D11) so a non-trusted document cannot trigger filesystem access
 * through macros during the lifting.
 *
 * Detection (decision D3) is opt-in and heuristic-free: a table is semantic only
 * when it carries an explicit annotation — the role `semantic-<name>`
 * (`[.semantic-<name>]`) or the block attribute `semantic=<name>`
 * (`[semantic=<name>]`). Any other table is ignored (the first one wins when
 * both are present, block attribute taking precedence).
 */
class AsciidoctorTableReader(
    private val safeMode: SafeMode = SafeMode.UNSAFE,
) : SemanticTableReader {

    override fun read(source: File): List<RawTable> {
        if (!source.exists() || !source.isFile) return emptyList()
        val options = Options.builder()
            .safe(safeMode)
            .option("sourcemap", "true")
            .build()
        val document = AsciidoctorHolder.load(source, options)
        val tables = mutableListOf<RawTable>()
        collect(document, tables)
        return tables
    }

    private fun collect(node: StructuralNode, sink: MutableList<RawTable>) {
        if (node is Table) {
            annotationName(node)?.let { sink += toRawTable(it, node) }
            return
        }
        node.blocks.orEmpty().forEach { collect(it, sink) }
    }

    /**
     * Returns the opt-in annotation name of [node], or `null` when the table is
     * not annotated (and must therefore be ignored). The block attribute
     * `semantic=<name>` takes precedence over the `semantic-<name>` role.
     */
    internal fun annotationName(node: ContentNode): String? {
        val attribute = node.attributes?.get(ANNOTATION_ATTRIBUTE)?.toString()?.trim()
        if (!attribute.isNullOrBlank()) return attribute
        return node.roles.orEmpty()
            .firstOrNull { it.startsWith(ANNOTATION_ROLE_PREFIX) }
            ?.removePrefix(ANNOTATION_ROLE_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun toRawTable(name: String, table: Table): RawTable =
        RawTable(
            name = name,
            header = table.header.orEmpty().firstOrNull()?.cells.orEmpty().map { it.cellText() },
            rows = table.body.orEmpty().map { row -> row.cells.orEmpty().map { it.cellText() } },
        )

    private fun Cell.cellText(): String = text?.trim() ?: ""

    internal companion object {
        /** The block attribute carrying the schema name (`[semantic=<name>]`). */
        const val ANNOTATION_ATTRIBUTE = "semantic"

        /** The role prefix carrying the schema name (`[.semantic-<name>]`). */
        const val ANNOTATION_ROLE_PREFIX = "semantic-"
    }
}
