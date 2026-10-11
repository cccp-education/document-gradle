package document.translation.tree

import document.translation.PivotArticle
import document.translation.PivotBlock
import document.translation.PivotFrontmatter
import document.translation.PivotInline
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * GBL-010 G-2 — the article as a document: its AsciiDoc AST as a tree.
 *
 * The user's vision: *"chaque article est à prendre comme un document, et chaque
 * document a l'arbre du document AsciiDoc (AST) dedans"*. The pivot of
 * `document.translation` is a **flat** block list; the tree is the missing shape
 * that groups it under its headings and surfaces the leaves the plantuml borough
 * owns (`PivotBlock.Source(language = "plantuml")`).
 */
class ArticleTreeAuditTest {

    @Test
    fun `headings open nested sections that own the following blocks`() {
        val content =
            content(
                heading(1, "Intro"),
                paragraph("Hello"),
                heading(2, "Details"),
                paragraph("More"),
            )

        val root = ArticleTreeAudit.build(content)

        assertThat(root.children).hasSize(1)
        val intro = root.children.single() as ArticleNode.Section
        assertThat(intro.title).isEqualTo("Intro")
        assertThat(intro.level).isEqualTo(1)
        val details = intro.children.filterIsInstance<ArticleNode.Section>().single()
        assertThat(details.title).isEqualTo("Details")
        assertThat(details.children.single()).isInstanceOf(ArticleNode.Leaf::class.java)
    }

    @Test
    fun `a plantuml block is a leaf owned by the plantuml borough`() {
        val report = ArticleTreeAudit.of(content(PivotBlock.Source("plantuml", "@startuml\nA -> B\n@enduml")))

        assertThat(report.plantumlLeaves).isEqualTo(1)
        assertThat(report.hasDiagrams).isTrue
    }

    @Test
    fun `a non-plantuml source block is a document leaf`() {
        val report = ArticleTreeAudit.of(content(PivotBlock.Source("java", "class A {}")))

        assertThat(report.plantumlLeaves).isZero()
        assertThat(report.hasDiagrams).isFalse
        assertThat(leavesOf(report).single().owner).isEqualTo(ArticleLeafOwner.DOCUMENT)
    }

    @Test
    fun `the report counts the translatable leaves`() {
        val report =
            ArticleTreeAudit.of(
                content(paragraph("a"), paragraph("b"), PivotBlock.Source("plantuml", "x")),
            )

        assertThat(report.translatableLeaves).isEqualTo(2)
        assertThat(report.blocks).isEqualTo(3)
    }

    @Test
    fun `the render is deterministic and names the plantuml owner`() {
        val report = ArticleTreeAudit.of(content(heading(1, "T"), PivotBlock.Source("plantuml", "x")))

        val rendered = report.render()

        assertThat(rendered).contains("T")
        assertThat(rendered).contains("plantuml")
        // Ink economy: a deterministic report carries no timestamp.
        assertThat(rendered).doesNotContain("2026-")
    }

    private fun leavesOf(report: ArticleTreeReport): List<ArticleNode.Leaf> {
        fun walk(node: ArticleNode): List<ArticleNode.Leaf> =
            when (node) {
                is ArticleNode.Leaf -> listOf(node)
                is ArticleNode.Section -> node.children.flatMap(::walk)
            }
        return walk(report.root)
    }

    private fun heading(level: Int, text: String) = PivotBlock.Heading(level, text, true)

    private fun paragraph(text: String) = PivotBlock.Paragraph(listOf(PivotInline.Text(text, true)))

    private fun content(vararg blocks: PivotBlock) =
        Content(
            PivotArticle(
                PivotFrontmatter("T", "2026-01-01", "post", "published"),
                blocks.toList(),
            ),
        )
}
