package document.translation.tree

import document.translation.PivotArticle
import document.translation.PivotBlock
import document.translation.PivotFrontmatter
import document.translation.PivotInline
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * Steps for `article_tree.feature` (GBL-010 G-2).
 *
 * The glue package is `document.translation.tree` (the runner's `GLUE_PROPERTY_NAME`),
 * so these names must not collide with another runner's steps.
 */
class ArticleTreeSteps {

    private val blocks = mutableListOf<PivotBlock>()
    private var built: ArticleNode.Section? = null
    private var report: ArticleTreeReport? = null

    @Given("an article with a level 1 heading {string} holding a paragraph")
    fun aLevel1Heading(title: String) {
        blocks.clear()
        blocks += PivotBlock.Heading(1, title, true)
        blocks += paragraph("p")
    }

    @Given("a level 2 heading {string} holding a paragraph")
    fun aLevel2Heading(title: String) {
        blocks += PivotBlock.Heading(2, title, true)
        blocks += paragraph("p")
    }

    @Given("an article with a plantuml block")
    fun aPlantumlBlock() {
        blocks.clear()
        blocks += PivotBlock.Source("plantuml", "@startuml\nA -> B\n@enduml")
    }

    @Given("an article with a java source block")
    fun aJavaSourceBlock() {
        blocks.clear()
        blocks += PivotBlock.Source("java", "class A {}")
    }

    @When("the article tree is built")
    fun theTreeIsBuilt() {
        built = ArticleTreeAudit.build(content())
    }

    @When("the article tree is audited")
    fun theTreeIsAudited() {
        report = ArticleTreeAudit.of(content())
    }

    @Then("the article tree has one top-level section {string}")
    fun oneTopLevelSection(title: String) {
        val sections = built!!.children.filterIsInstance<ArticleNode.Section>()
        assertThat(sections).hasSize(1)
        assertThat(sections.single().title).isEqualTo(title)
    }

    @Then("the section {string} owns one nested section {string}")
    fun ownsOneNestedSection(parent: String, child: String) {
        val rootSection =
            built!!.children.filterIsInstance<ArticleNode.Section>()
                .single { it.title == parent }
        val nested = rootSection.children.filterIsInstance<ArticleNode.Section>()
        assertThat(nested).hasSize(1)
        assertThat(nested.single().title).isEqualTo(child)
    }

    @Then("the article tree counts {int} plantuml leaves")
    fun countsPlantumlLeaves(count: Int) {
        assertThat(report!!.plantumlLeaves).isEqualTo(count)
    }

    @Then("the article has diagrams")
    fun hasDiagrams() {
        assertThat(report!!.hasDiagrams).isTrue
    }

    private fun content() =
        Content(
            PivotArticle(
                PivotFrontmatter("T", "2026-01-01", "post", "published"),
                blocks.toList(),
            ),
        )

    private fun paragraph(text: String) = PivotBlock.Paragraph(listOf(PivotInline.Text(text, true)))
}
