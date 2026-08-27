package document

/**
 * Hierarchical numbering, anchors and navigation for a structured [BookTree].
 *
 * [BookNumbering] is a pure DDD service of the "Book" domain. It turns the
 * structural position of a [BookNode] in the tree into a human-readable
 * hierarchical number (`1`, `1.2`, `1.2.1`), exposes an AsciiDoc cross-reference
 * anchor for a `ref`, and computes the previous / next leaf [BookSection] for
 * in-document navigation.
 *
 * The number of a node is its 1-based index among its siblings, prefixed by the
 * parent's number — the canonical book numbering, independent of the digits that
 * happen to appear in the source TOC `ref`. The synthetic root carries no number.
 *
 * Ink Economy Law: every method is a pure deterministic function of the tree —
 * no I/O, no Gradle dependency, fully unit-testable in isolation.
 */
object BookNumbering {

    /**
     * Computes the hierarchical number of every non-root node in [tree].
     *
     * The FPA table-of-contents convention encodes the book hierarchy directly
     * in the `ref` (`1`, `1.2`, `1.2.1`; `0.x` = front matter, `9.x` = back
     * matter), so the hierarchical number of a node *is* its `ref`. The map is
     * built by walking the tree so that every (real or synthetic) node is
     * covered, independent of the digits that happen to appear in the ref.
     *
     * @return a map from node `ref` to its hierarchical number (equal to the
     *   `ref`); the synthetic root is not present
     */
    fun numbers(tree: BookTree): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        fun walk(node: BookNode) {
            if (node.ref.isNotEmpty()) map[node.ref] = node.ref
            node.children.forEach { walk(it) }
        }
        walk(tree.root)
        return map
    }

    /**
     * Returns the hierarchical number of the node with [ref] in [tree], or
     * `null` when the node does not exist (or is the synthetic root).
     */
    fun number(tree: BookTree, ref: String): String? = numbers(tree)[ref]

    /**
     * Emits the AsciiDoc cross-reference anchor for [ref] (`[[ref]]`).
     */
    fun anchor(ref: String): String = "[[$ref]]"

    /**
     * Returns the previous and next leaf [BookSection]s around the section with
     * [ref], in [BookTree.leaves] (document) order. When [ref] is not a leaf,
     * both sides are `null`.
     */
    fun navigation(tree: BookTree, ref: String): BookNavigation {
        val leaves = tree.leaves
        val idx = leaves.indexOfFirst { it.ref == ref }
        if (idx < 0) return BookNavigation(null, null)
        val previous = if (idx > 0) leaves[idx - 1] else null
        val next = if (idx < leaves.lastIndex) leaves[idx + 1] else null
        return BookNavigation(previous, next)
    }
}
