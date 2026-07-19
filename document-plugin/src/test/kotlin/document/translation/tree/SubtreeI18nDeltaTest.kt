package document.translation.tree

import document.translation.PivotArticle
import document.translation.PivotBlock
import document.translation.PivotFrontmatter
import document.translation.PivotInline
import document.translation.tree.SiteNode.Article
import document.translation.tree.SiteNode.Section
import document.translation.tree.SiteNode.Site
import document.translation.plan.SubtreeI18nPlanner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SubtreeI18nDeltaTest {

    private fun sampleTree(): SiteTree {
        val ab = Article(path = "formations/ab-partition")
        val cd = Article(path = "formations/cd-partition")
        val formations = Section(path = "formations", articles = listOf(ab, cd))
        val blog = Section(path = "blog", articles = listOf(Article(path = "blog/hello")))
        return SiteTree(Site(path = "", sections = listOf(formations, blog)))
    }

    private fun content(text: String): Content =
        Content(PivotArticle(
            PivotFrontmatter("T", "2026-01-01", "page", "published"),
            listOf(PivotBlock.Paragraph(listOf(PivotInline.Text(text, translatable = true))))
        ))

    @Test
    fun `delta is empty when no source changed`() {
        val tree = sampleTree()
        val checksums = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val planner = SubtreeI18nPlanner(tree, checksums)

        val delta = planner.computeDelta(checksums)

        assertTrue(delta.modifiedArticles.isEmpty())
        assertTrue(delta.untouchedArticles.size == 3)
    }

    @Test
    fun `delta detects a single modified article in the whole tree`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply { this["formations/ab-partition"] = "hash-ab-v2" }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after)

        assertEquals(1, delta.modifiedArticles.size)
        assertEquals("formations/ab-partition", delta.modifiedArticles[0].path)
        assertEquals(2, delta.untouchedArticles.size)
    }

    @Test
    fun `delta for a subtree only includes modified articles within that subtree`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply {
            this["formations/ab-partition"] = "hash-ab-v2"
            this["blog/hello"] = "hash-blog-v2"
        }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after, subtreePath = "formations")

        assertEquals(1, delta.modifiedArticles.size)
        assertEquals("formations/ab-partition", delta.modifiedArticles[0].path)
        assertFalse(delta.modifiedArticles.any { it.path == "blog/hello" })
    }

    @Test
    fun `integrity outside subtree is preserved - blog article not in delta`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply { this["blog/hello"] = "hash-blog-v2" }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after, subtreePath = "formations")

        assertTrue(delta.modifiedArticles.isEmpty())
        assertEquals(2, delta.untouchedArticles.size)
        assertTrue(delta.untouchedArticles.any { it.path == "formations/ab-partition" })
    }

    @Test
    fun `idempotence - re-execution without source change yields empty delta`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply { this["formations/ab-partition"] = "hash-ab-v2" }
        val planner = SubtreeI18nPlanner(tree, before)

        val firstRun = planner.computeDelta(after)
        assertEquals(1, firstRun.modifiedArticles.size)

        val replanner = SubtreeI18nPlanner(tree, firstRun.updatedChecksums)
        val secondRun = replanner.computeDelta(firstRun.updatedChecksums)
        assertTrue(secondRun.modifiedArticles.isEmpty())
    }

    @Test
    fun `delta carries translatable segment count per modified article`() {
        val tree = SiteTree(Site(path = "", sections = listOf(
            Section(path = "formations", articles = listOf(
                Article(path = "formations/ab-partition", content = content("Hello world")),
                Article(path = "formations/cd-partition", content = content("Bonjour le monde"))
            ))
        )))
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1"
        )
        val after = before.toMutableMap().apply { this["formations/ab-partition"] = "hash-ab-v2" }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after)

        assertEquals(1, delta.modifiedArticles.size)
        val mod = delta.modifiedArticles[0]
        assertEquals("formations/ab-partition", mod.path)
        assertEquals(1, mod.translatableSegmentCount)
    }

    @Test
    fun `new article not in before checksums is included as modified`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply { this["formations/ab-partition"] = "hash-ab-new" }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after)

        assertEquals(1, delta.modifiedArticles.size)
        assertEquals("formations/ab-partition", delta.modifiedArticles[0].path)
    }

    @Test
    fun `deleted article checksum in before but not in after is not a modification`() {
        val tree = SiteTree(Site(path = "", sections = listOf(
            Section(path = "formations", articles = listOf(
                Article(path = "formations/cd-partition")
            ))
        )))
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1"
        )
        val after = mapOf("formations/cd-partition" to "hash-cd-v1")
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after)

        assertTrue(delta.modifiedArticles.isEmpty())
        assertEquals(1, delta.untouchedArticles.size)
    }

    @Test
    fun `backward compat - no tree falls back to flat walk of all articles`() {
        val articles = listOf(
            Article(path = "formations/ab-partition"),
            Article(path = "formations/cd-partition"),
            Article(path = "blog/hello")
        )
        val before = articles.associate { it.path to "hash-${it.path}-v1" }
        val planner = SubtreeI18nPlanner(articles, before)

        val after = before.toMutableMap().apply { this["blog/hello"] = "hash-blog-v2" }
        val delta = planner.computeDelta(after)

        assertEquals(1, delta.modifiedArticles.size)
        assertEquals("blog/hello", delta.modifiedArticles[0].path)
    }

    @Test
    fun `delta for subtree with no modified articles yields empty result`() {
        val tree = sampleTree()
        val before = mapOf(
            "formations/ab-partition" to "hash-ab-v1",
            "formations/cd-partition" to "hash-cd-v1",
            "blog/hello" to "hash-blog-v1"
        )
        val after = before.toMutableMap().apply { this["blog/hello"] = "hash-blog-v2" }
        val planner = SubtreeI18nPlanner(tree, before)

        val delta = planner.computeDelta(after, subtreePath = "formations")

        assertTrue(delta.modifiedArticles.isEmpty())
    }
}
