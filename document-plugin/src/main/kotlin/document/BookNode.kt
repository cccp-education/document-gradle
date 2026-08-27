package document

/**
 * A node in the structured book tree.
 *
 * A [BookNode] is an immutable value object representing one entry of the book
 * hierarchy (Part, Chapter, Section, Sub-section, …). The hierarchy is derived
 * from the table-of-contents `ref` (e.g. `1.0.2.6.4`): the [level] equals the
 * number of segments minus one, and the parent of a node is the node whose
 * `ref` is the same prefix truncated to the last segment.
 *
 * A node is a *leaf* when it is mapped to an OCR-ed [BookSection] (its
 * [source]); purely structural ancestors that never appear as their own TOC
 * row have a `null` [source] (they are synthesized by [BookTreeBuilder]).
 *
 * Ink Economy Law: the node is a pure DDD value object — no I/O, no Gradle
 * dependency, fully deterministic and testable in isolation.
 */
data class BookNode(
    val ref: String,
    val title: String,
    val children: List<BookNode> = emptyList(),
    val source: BookSection? = null,
) {

    init {
        require(ref == "" || REF_PATTERN.matches(ref)) {
            "BookNode ref must be blank (root) or a dotted numeric ref, got: '$ref'"
        }
    }

    /**
     * Depth of the node in the hierarchy.
     *
     * `level = (number of segments in ref) - 1`. The synthetic root (`ref = ""`)
     * has level 0; `1.0.2.6.4` (5 segments) has level 4. Each `ref` extension
     * increases the level by exactly one, so the tree nesting is always
     * monotonic — a parent is always one level shallower than its child.
     */
    val level: Int
        get() = if (ref.isEmpty()) 0 else ref.split('.').count { it.isNotEmpty() } - 1

    companion object {
        private val REF_PATTERN = Regex("""^\d+(\.\d+)*$""")
    }
}
