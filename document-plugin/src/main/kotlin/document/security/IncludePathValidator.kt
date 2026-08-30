package document.security

import java.io.File

/**
 * Pure DDD validator auditing `include::` directives of an AsciiDoc document
 * against an allowed filesystem root (DOC-CR4-1).
 *
 * A forbidden include is one that :
 *  - uses an absolute path (`/etc/passwd`), or
 *  - resolves (after `..` normalisation) outside [allowedRoot].
 *
 * The guard is a *pre-flight* audit : it protects the conversion of untrusted
 * AsciiDoc (OCR/LLM-produced) from reading files outside the document tree,
 * complementing the AsciidoctorJ [org.asciidoctor.SafeMode] restriction
 * introduced in DOC-CR3-2.
 *
 * Ink Economy Law : resolution is deterministic and side-effect free; no file
 * is read, only path canonicalisation is computed (works for non-existent
 * targets too).
 */
object IncludePathValidator {

    private val PATTERN = Regex("""include::\s*("(?:[^"]*)"|[^\[\s]+)(\s*\[[^\]]*\])?""")

    /**
     * Extracts every `include::target[opts]` directive from [text] with its
     * 1-based line number. Quoted targets (`include::"my file.adoc"[]`) have
     * their surrounding quotes stripped.
     */
    fun parseIncludes(text: String): List<IncludeDirective> {
        val includes = mutableListOf<IncludeDirective>()
        text.lines().forEachIndexed { index, line ->
            PATTERN.findAll(line).forEach { match ->
                var target = match.groupValues[1]
                if (target.startsWith("\"") && target.endsWith("\"")) {
                    target = target.removeSurrounding("\"")
                }
                includes += IncludeDirective(match.value, target, index + 1)
            }
        }
        return includes
    }

    /**
     * Audits [text] for forbidden includes. [baseDir] is the directory the
     * relative includes are resolved against (usually the source file parent).
     * [allowedRoot] is the directory includes must stay under (defaults to
     * [baseDir]).
     *
     * @return [IncludeValidationResult.Valid] when every include is contained,
     *   otherwise the first [IncludeValidationResult.Invalid] finding.
     */
    fun validate(text: String, baseDir: File, allowedRoot: File = baseDir): IncludeValidationResult {
        val root = allowedRoot.canonicalFile
        val rootPrefix = root.path + File.separator
        for (directive in parseIncludes(text)) {
            val target = directive.target
            if (File(target).isAbsolute) {
                return IncludeValidationResult.Invalid("absolute path not allowed", target, directive.line)
            }
            val resolved = File(baseDir, target).canonicalFile
            if (resolved.path != root.path && !resolved.path.startsWith(rootPrefix)) {
                return IncludeValidationResult.Invalid("include escapes allowed root", target, directive.line)
            }
        }
        return IncludeValidationResult.Valid
    }
}
