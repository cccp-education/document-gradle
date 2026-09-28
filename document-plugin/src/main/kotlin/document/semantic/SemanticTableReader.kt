package document.semantic

import java.io.File

/**
 * Port of the semantic table reading (EPIC DOC-SEMANTIC-TABLE, US-2, decision
 * D6) — Gradle-free and AsciidoctorJ-free.
 *
 * The adapter ([AsciidoctorTableReader]) bridges the real AsciidoctorJ object
 * model (`org.asciidoctor.ast.*`); unit tests inject a plain fake. The port is
 * stateless: it must be reusable across calls (Ink Economy Law — a pure
 * deterministic function of the source file).
 *
 * Only the tables annotated opt-in (`[.semantic-<name>]` or `[semantic=<name>]`,
 * decision D3) are returned; every other table is ignored. Implementations must
 * not throw for a missing file — an absent document yields an empty list.
 */
fun interface SemanticTableReader {

    /**
     * Reads [source] (an AsciiDoc file) and returns the annotated tables in
     * document order, as Gradle-free [RawTable] values.
     */
    fun read(source: File): List<RawTable>
}
