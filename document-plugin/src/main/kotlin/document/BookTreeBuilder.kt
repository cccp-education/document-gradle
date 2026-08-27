package document

/**
 * Rebuilds the book hierarchy from a flat list of [BookSection]s.
 *
 * [BookTreeBuilder.fromSections] derives the structured [BookTree] purely from
 * the `ref` of each section (e.g. `1.0.2.6.4`):
 * - the [BookNode.level] is the number of segments minus one;
 * - the parent of a node is the node whose `ref` is the same prefix truncated
 *   to the last segment (`1.0.2.6` is the parent of `1.0.2.6.4`);
 * - missing ancestors (an orphan `ref` whose parent never appears in the TOC)
 *   are synthesized so the tree is always structurally complete.
 *
 * Multiple [BookSection]s sharing the same `ref` (multi-page expansion done by
 * [BookTocParser]) collapse into a single [BookNode]; the first section wins as
 * the node's [BookNode.source] and every section is retained in [BookTree.leaves]
 * (the source of truth for page content).
 *
 * Ink Economy Law: the build is pure and deterministic — the same sections
 * always yield the same tree, and no file is read or mutated.
 */
object BookTreeBuilder {

    private val REF_PATTERN = Regex("""^\d+(\.\d+)*$""")

    /**
     * Builds the [BookTree] for [sections].
     *
     * @return the structured tree; for an empty [sections] list, an empty tree
     *   with a synthetic root and no nodes
     */
    fun fromSections(sections: List<BookSection>): BookTree {
        if (sections.isEmpty()) {
            return BookTree(
                root = BookNode(ref = "", title = "Book", children = emptyList()),
                nodesByRef = emptyMap(),
                leaves = emptyList(),
            )
        }

        // 1. unique refs (first section wins), preserving insertion order
        val orderedUnique = LinkedHashMap<String, BookSection>()
        for (section in sections) orderedUnique.putIfAbsent(section.ref, section)

        // 2. skeleton nodes for every real ref
        val skeleton = LinkedHashMap<String, BookNode>()
        for ((ref, section) in orderedUnique) {
            skeleton[ref] = BookNode(ref = ref, title = section.title, source = section)
        }

        // 3. ensure every ancestor ref exists (synthetic, no source page)
        val pending = skeleton.keys.toMutableList()
        while (pending.isNotEmpty()) {
            val ref = pending.removeAt(0)
            val parent = parentRefOf(ref)
            if (parent != null && parent !in skeleton) {
                skeleton[parent] = BookNode(ref = parent, title = "", source = null)
                pending.add(parent)
            }
        }

        // 4. group child refs under their parent, sorted for determinism
        val childrenByParent = LinkedHashMap<String, MutableList<String>>()
        childrenByParent[""] = mutableListOf()
        for (ref in skeleton.keys) childrenByParent[ref] = mutableListOf()
        for (ref in skeleton.keys) {
            val parent = parentRefOf(ref)
            if (parent == null) childrenByParent[""]!!.add(ref)
            else childrenByParent[parent]!!.add(ref)
        }
        for (list in childrenByParent.values) list.sort()

        // 5. build immutable nodes bottom-up (children resolved recursively)
        fun build(ref: String): BookNode {
            val base = skeleton[ref]!!
            val kids = childrenByParent[ref]!!.map { build(it) }
            return base.copy(children = kids)
        }
        val rootChildren = childrenByParent[""]!!.map { build(it) }
        val root = BookNode(ref = "", title = "Book", children = rootChildren)

        // 6. index every node (real + synthetic) by ref
        val index = LinkedHashMap<String, BookNode>()
        fun collect(node: BookNode) {
            index[node.ref] = node
            node.children.forEach { collect(it) }
        }
        collect(root)

        return BookTree(root = root, nodesByRef = index, leaves = sections)
    }

    /**
     * Returns the parent `ref` of [ref], or `null` when [ref] is a top-level
     * ref (no dot) whose parent is the synthetic root.
     */
    private fun parentRefOf(ref: String): String? {
        require(ref == "" || REF_PATTERN.matches(ref)) { "invalid ref: '$ref'" }
        val idx = ref.lastIndexOf('.')
        return if (idx <= 0) null else ref.substring(0, idx)
    }
}
