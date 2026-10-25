package document.translation.tree

/**
 * GBL-010 G-2 — a node of an article's AsciiDoc AST tree.
 *
 * The pivot ([document.translation.PivotBlock] list) is **flat**: headings and
 * their content are siblings. This tree is the missing shape — it groups each
 * block under the heading that owns it, and flags the leaves the plantuml borough
 * owns.
 *
 * Pure domain: no I/O, no Gradle, no LLM.
 */
sealed interface ArticleNode {

    val owner: ArticleLeafOwner

    /** A heading and everything it owns until the next heading of the same or lower level. */
    data class Section(
        val level: Int,
        val title: String,
        val children: List<ArticleNode>,
    ) : ArticleNode {
        override val owner: ArticleLeafOwner get() = ArticleLeafOwner.DOCUMENT
    }

    /**
     * A concrete block: prose (document) or a diagram (plantuml).
     *
     * [status] is reserved for leaves whose status is delegated to another
     * borough — today the plantuml leaf (GBL-010 G-2b). Prose leaves keep it
     * `null`: their translation status is already carried by [translatable]
     * and the block delta.
     */
    data class Leaf(
        val kind: String,
        val translatable: Boolean,
        override val owner: ArticleLeafOwner,
        val status: ArticleLeafStatus? = null,
    ) : ArticleNode
}

/** The borough responsible for a leaf: document for prose, plantuml for diagrams. */
enum class ArticleLeafOwner {
    DOCUMENT,
    PLANTUML,
}
