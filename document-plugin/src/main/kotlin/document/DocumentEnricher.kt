package document

import java.io.File

/**
 * AsciiDoc document enricher applied before multi-format conversion.
 *
 * Ubiquitous language: a [DocumentEnricher] transforms a [DocumentSource]
 * into a resolved AsciiDoc document without ever mutating the source (Rule 7).
 *
 * DOC-9:
 * - preserves inline `[plantuml]` blocks (rendered at conversion time by Asciidoctor Diagram)
 * - preserves passthrough `++++` blocks (raw HTML) without escaping them
 * - recursively resolves `include::` directives (AsciidoctorJ resolves them natively
 *   at conversion time, but enrichment materializes the content for audit purposes)
 *
 * Ink Economy Law: enrichment is idempotent — a given source document always
 * produces the same enriched document.
 */
object DocumentEnricher {

    private val INCLUDE_PATTERN = Regex("""include::([^\[\]]+)\[\]""")
    private const val MAX_INCLUDE_DEPTH = 10

    /**
     * Enriches a [DocumentSource] into a resolved AsciiDoc document.
     *
     * @param source the source AsciiDoc file (read-only)
     * @return the enriched AsciiDoc content (includes resolved, blocks preserved)
     */
    fun enrich(source: DocumentSource): String {
        return resolveIncludes(source.file, depth = 0)
    }

    private fun resolveIncludes(file: File, depth: Int): String {
        require(depth <= MAX_INCLUDE_DEPTH) {
            "Include recursion depth exceeded ($MAX_INCLUDE_DEPTH) — possible circular include in ${file.name}"
        }
        val content = file.readText()
        val baseDir = file.parentFile ?: File(".")
        return INCLUDE_PATTERN.replace(content) { match ->
            val includedPath = match.groupValues[1]
            val includedFile = File(baseDir, includedPath)
            if (includedFile.exists() && includedFile.isFile) {
                resolveIncludes(includedFile, depth + 1)
            } else {
                match.value
            }
        }
    }
}