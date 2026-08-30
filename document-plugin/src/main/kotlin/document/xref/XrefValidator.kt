package document.xref

/**
 * Pure DDD service validating AsciiDoc cross-references (`<<id>>` / `xref:id[]`).
 *
 * The validator scans the raw AsciiDoc text and reconciles two sets :
 * - *defined anchors* : `[[id]]`, `[[id,label]]` (inline/block anchor) and `[#id]`
 *   (block id shorthand) ;
 * - *referenced anchors* : `<<id>>`, `<<id,text>>`, `xref:id[]`, `xref:id[text]`.
 *
 * Any referenced id with no matching definition is reported as unresolved.
 *
 * Ink Economy Law: every method is a pure deterministic function of the input text
 * — no I/O, no Gradle dependency, fully unit-testable in isolation. The heuristic
 * is intentionally textual (parallel to [document.security.IncludePathValidator]),
 * which is sufficient for authored documents and for the Book domain where anchors
 * are explicit (`[[ref]]`). Implicit Asciidoctor auto-ids (derived from section
 * titles) are intentionally out of scope.
 */
object XrefValidator {

    private val ANCHOR = Regex("""\[\[([A-Za-z0-9_][A-Za-z0-9_.\-]*)(,[^\]]*)?\]\]""")
    private val BLOCK_ID = Regex("""\[#([A-Za-z0-9_][A-Za-z0-9_.\-]*)""")
    private val REF_ANGLE = Regex("""<<([A-Za-z0-9_][A-Za-z0-9_.\-]*)(,[^>]*)?>>""")
    private val REF_XREF = Regex("""xref:([A-Za-z0-9_][A-Za-z0-9_.\-]*)(\[[^\]]*\])?""")

    /**
     * Extracts the set of defined anchor ids in [text] (anchors + block ids,
     * deduplicated).
     */
    fun extractAnchors(text: String): Set<String> {
        val anchors = ANCHOR.findAll(text).map { it.groupValues[1] }.toSet()
        val blockIds = BLOCK_ID.findAll(text).map { it.groupValues[1] }.toSet()
        return anchors + blockIds
    }

    /**
     * Extracts the list of referenced anchor ids in [text], in document order
     * (duplicates preserved — they all must resolve).
     */
    fun extractReferences(text: String): List<String> {
        val refs = mutableListOf<String>()
        REF_ANGLE.findAll(text).forEach { refs += it.groupValues[1] }
        REF_XREF.findAll(text).forEach { refs += it.groupValues[1] }
        return refs
    }

    /**
     * Validates [text] : returns [XrefValidationResult.Valid] when every referenced
     * anchor is defined, otherwise [XrefValidationResult.Invalid] carrying the sorted,
     * deduplicated list of unresolved ids.
     */
    fun validate(text: String): XrefValidationResult {
        val defined = extractAnchors(text)
        val missing = extractReferences(text)
            .filter { it !in defined }
            .distinct()
            .sorted()
        return if (missing.isEmpty()) {
            XrefValidationResult.Valid
        } else {
            XrefValidationResult.Invalid(missing)
        }
    }
}
