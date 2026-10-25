package document.translation.tree

import document.translation.PivotBlock

/**
 * GBL-010 G-2 — the article as a document: its AsciiDoc AST as a tree.
 *
 * *"Chaque article est à prendre comme un document, et chaque document a l'arbre
 * du document AsciiDoc (AST) dedans. On y trouvera des balises plantuml, et ce
 * sera le borough plantuml qui gérera sa traduction."*
 *
 * The pivot is a flat block list; this audit builds the **article-internal tree**
 * (sections by heading level, leaves grouped under their section) and inventories
 * it: how many leaves are translatable (document's job) and how many are PlantUML
 * blocks (the plantuml leaf). It is the missing node of the translation tree
 * between the site and the diagram.
 *
 * Pure domain: no I/O, no Gradle, no LLM.
 */
object ArticleTreeAudit {

    const val PLANTUML = "plantuml"

    /**
     * Builds and audits the article tree, resolving each PlantUML leaf status
     * through the N0 plantuml contracts (GBL-010 G-2b).
     */
    fun of(content: Content, statusResolver: PlantUmlLeafStatusResolver): ArticleTreeReport {
        val root = build(content, statusResolver)
        val leaves = leavesOf(root)
        val plantumlStatuses =
            leaves
                .filter { it.owner == ArticleLeafOwner.PLANTUML }
                .mapNotNull { it.status }
        return ArticleTreeReport(
            root = root,
            blocks = content.blocs().size,
            translatableLeaves = leaves.count { it.translatable },
            plantumlLeaves = leaves.count { it.owner == ArticleLeafOwner.PLANTUML },
            plantumlStatusCounts =
                ArticleLeafStatus.entries.associateWith { status ->
                    plantumlStatuses.count { it == status }
                },
        )
    }

    /**
     * Builds the article tree without resolving leaf statuses; the root section
     * has level 0 and an empty title. Used when only the shape is needed.
     */
    fun build(content: Content): ArticleNode.Section = build(content, statusResolver = null)

    /**
     * Builds the article tree, resolving each PlantUML leaf status when a
     * [statusResolver] is supplied.
     */
    internal fun build(content: Content, statusResolver: PlantUmlLeafStatusResolver?): ArticleNode.Section {
        val root = Building.Section(0, "")
        val stack = ArrayDeque<Building.Section>()
        stack.addLast(root)

        content.blocs().forEach { block ->
            if (block is PivotBlock.Heading && block.level > 0) {
                while (stack.size > 1 && stack.last().level >= block.level) {
                    stack.removeLast()
                }
                val section = Building.Section(block.level, block.text)
                stack.last().children += section
                stack.addLast(section)
            } else {
                stack.last().children += Building.Leaf(leafOf(block, statusResolver))
            }
        }

        return root.freeze()
    }

    fun leavesOf(node: ArticleNode): List<ArticleNode.Leaf> =
        when (node) {
            is ArticleNode.Leaf -> listOf(node)
            is ArticleNode.Section -> node.children.flatMap { leavesOf(it) }
        }

    private fun leafOf(block: PivotBlock, statusResolver: PlantUmlLeafStatusResolver?): ArticleNode.Leaf =
        when (block) {
            is PivotBlock.Heading ->
                ArticleNode.Leaf("heading", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.Paragraph ->
                ArticleNode.Leaf("paragraph", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.ListBlock ->
                ArticleNode.Leaf("list", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.Table ->
                ArticleNode.Leaf("table", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.Admonition ->
                ArticleNode.Leaf("admonition", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.DescriptionList ->
                ArticleNode.Leaf("description-list", block.translatable, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.Source ->
                if (block.language.equals(PLANTUML, ignoreCase = true)) {
                    ArticleNode.Leaf(
                        kind = PLANTUML,
                        translatable = false,
                        owner = ArticleLeafOwner.PLANTUML,
                        status = statusResolver?.resolve(block),
                    )
                } else {
                    ArticleNode.Leaf("source", false, ArticleLeafOwner.DOCUMENT)
                }

            is PivotBlock.BlockMacro ->
                ArticleNode.Leaf("block-macro", false, ArticleLeafOwner.DOCUMENT)

            is PivotBlock.Hr ->
                ArticleNode.Leaf("hr", false, ArticleLeafOwner.DOCUMENT)
        }

    /** A mutable building node, frozen into the immutable [ArticleNode] tree. */
    private sealed interface Building {
        fun freeze(): ArticleNode

        class Section(val level: Int, val title: String) : Building {
            val children = mutableListOf<Building>()

            override fun freeze(): ArticleNode.Section =
                ArticleNode.Section(level, title, children.map { it.freeze() })
        }

        class Leaf(val node: ArticleNode.Leaf) : Building {
            override fun freeze(): ArticleNode = node
        }
    }
}

data class ArticleTreeReport(
    val root: ArticleNode.Section,
    val blocks: Int,
    val translatableLeaves: Int,
    val plantumlLeaves: Int,
    val plantumlStatusCounts: Map<ArticleLeafStatus, Int> = emptyMap(),
) {
    val hasDiagrams: Boolean get() = plantumlLeaves > 0

    /** Deterministic AsciiDoc rendering (ink economy: no timestamp). */
    fun render(): String =
        buildString {
            appendLine("= Article AST tree")
            appendLine()
            appendLine(
                "Blocks: $blocks · translatable: $translatableLeaves · plantuml: $plantumlLeaves",
            )
            if (plantumlStatusCounts.isNotEmpty()) {
                val statuses =
                    ArticleLeafStatus.entries
                        .mapNotNull { status ->
                            plantumlStatusCounts[status]
                                ?.takeIf { it > 0 }
                                ?.let { "$status=$it" }
                        }
                if (statuses.isNotEmpty()) {
                    appendLine("PlantUML status: ${statuses.joinToString(" · ")}")
                }
            }
            appendLine()
            root.children.forEach { renderNode(it, 1) }
        }

    private fun StringBuilder.renderNode(node: ArticleNode, depth: Int) {
        val indent = "  ".repeat(depth - 1)
        when (node) {
            is ArticleNode.Section -> {
                appendLine("$indent* ${node.title}")
                node.children.forEach { renderNode(it, depth + 1) }
            }

            is ArticleNode.Leaf -> {
                val status = node.status?.let { " [$it]" } ?: ""
                appendLine("$indent- ${node.kind} (${node.owner.name.lowercase()})$status")
            }
        }
    }
}
