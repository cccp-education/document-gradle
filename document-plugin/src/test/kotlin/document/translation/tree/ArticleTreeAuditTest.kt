package document.translation.tree

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
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
        val report =
            ArticleTreeAudit.of(
                content(PivotBlock.Source("plantuml", "@startuml\nA -> B\n@enduml")),
                resolver(),
            )

        assertThat(report.plantumlLeaves).isEqualTo(1)
        assertThat(report.hasDiagrams).isTrue
    }

    @Test
    fun `a non-plantuml source block is a document leaf`() {
        val report = ArticleTreeAudit.of(content(PivotBlock.Source("java", "class A {}")), resolver())

        assertThat(report.plantumlLeaves).isZero()
        assertThat(report.hasDiagrams).isFalse
        assertThat(leavesOf(report).single().owner).isEqualTo(ArticleLeafOwner.DOCUMENT)
    }

    @Test
    fun `the report counts the translatable leaves`() {
        val report =
            ArticleTreeAudit.of(
                content(paragraph("a"), paragraph("b"), PivotBlock.Source("plantuml", "x")),
                resolver(),
            )

        assertThat(report.translatableLeaves).isEqualTo(2)
        assertThat(report.blocks).isEqualTo(3)
    }

    @Test
    fun `the render is deterministic and names the plantuml owner`() {
        val report =
            ArticleTreeAudit.of(
                content(heading(1, "T"), PivotBlock.Source("plantuml", "x")),
                resolver(),
            )

        val rendered = report.render()

        assertThat(rendered).contains("T")
        assertThat(rendered).contains("plantuml")
        // Ink economy: a deterministic report carries no timestamp.
        assertThat(rendered).doesNotContain("2026-")
    }

    @Test
    fun `a blank plantuml block is missing`() {
        val report =
            ArticleTreeAudit.of(
                content(PivotBlock.Source("plantuml", "   ")),
                resolver(),
            )

        assertThat(report.plantumlStatusCounts[ArticleLeafStatus.MISSING]).isEqualTo(1)
        assertThat(leavesOf(report).single().status).isEqualTo(ArticleLeafStatus.MISSING)
    }

    @Test
    fun `a plantuml block with translatable labels is pending`() {
        val source = PivotBlock.Source("plantuml", "@startuml\nrectangle \"Bonjour\"\n@enduml")

        val report = ArticleTreeAudit.of(content(source), resolver())

        assertThat(report.plantumlStatusCounts[ArticleLeafStatus.PENDING]).isEqualTo(1)
        assertThat(leavesOf(report).single().status).isEqualTo(ArticleLeafStatus.PENDING)
    }

    @Test
    fun `a plantuml block with no translatable labels is translated`() {
        val report =
            ArticleTreeAudit.of(
                content(PivotBlock.Source("plantuml", "@startuml\nA -> B\n@enduml")),
                resolver(),
            )

        assertThat(report.plantumlStatusCounts[ArticleLeafStatus.TRANSLATED]).isEqualTo(1)
        assertThat(leavesOf(report).single().status).isEqualTo(ArticleLeafStatus.TRANSLATED)
    }

    @Test
    fun `an invalid plantuml block is broken`() {
        val invalid = PlantUmlSyntaxValidator {
            SyntaxValidationResult.Invalid("syntax error", "trace")
        }

        val report =
            ArticleTreeAudit.of(
                content(PivotBlock.Source("plantuml", "@startuml\nrectangle \"Bonjour\"\n@enduml")),
                resolver(invalid),
            )

        assertThat(report.plantumlStatusCounts[ArticleLeafStatus.BROKEN]).isEqualTo(1)
        assertThat(leavesOf(report).single().status).isEqualTo(ArticleLeafStatus.BROKEN)
    }

    @Test
    fun `the report renders the delegated status per plantuml leaf`() {
        val report =
            ArticleTreeAudit.of(
                content(PivotBlock.Source("plantuml", "@startuml\nrectangle \"Bonjour\"\n@enduml")),
                resolver(),
            )

        assertThat(report.render()).contains("PlantUML status: PENDING=1")
        assertThat(report.render()).contains("plantuml (plantuml) [PENDING]")
    }

    private val validValidator: PlantUmlSyntaxValidator =
        PlantUmlSyntaxValidator { SyntaxValidationResult.Valid }

    private fun resolver(
        validator: PlantUmlSyntaxValidator = validValidator,
    ): PlantUmlLeafStatusResolver = PlantUmlLeafStatusResolver(validator)

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
