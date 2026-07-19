package document.translation.delta

import document.translation.tree.SiteNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nDeltaApplierTest {

    @Test
    fun `fresh migration with no existing target files translates all articles`() {
        val delta = I18nDelta(
            modifiedArticles = listOf(
                ArticleModification("blog/post-1", null, "abc123", 5),
                ArticleModification("blog/post-2", null, "def456", 3)
            ),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "abc123", "blog/post-2" to "def456")
        )
        val applier = I18nDeltaApplier(delta, existingTargetFiles = emptySet())

        val result = applier.apply()

        assertEquals(setOf("blog/post-1", "blog/post-2"), result.toTranslate.paths.toSet())
        assertTrue(result.toPreserve.paths.isEmpty())
    }

    @Test
    fun `delta with one modified article translates only that one`() {
        val delta = I18nDelta(
            modifiedArticles = listOf(
                ArticleModification("blog/post-1", "old-hash", "new-hash", 5)
            ),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "new-hash", "blog/post-2" to "stable-hash")
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1", "blog/post-2")
        )

        val result = applier.apply()

        assertEquals(setOf("blog/post-1"), result.toTranslate.paths.toSet())
        assertEquals(setOf("blog/post-2"), result.toPreserve.paths.toSet())
    }

    @Test
    fun `untouched articles that exist in target are preserved`() {
        val delta = I18nDelta(
            modifiedArticles = emptyList(),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "hash-v1", "blog/post-2" to "hash-v2")
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1", "blog/post-2")
        )

        val result = applier.apply()

        assertTrue(result.toTranslate.paths.isEmpty())
        assertEquals(setOf("blog/post-1", "blog/post-2"), result.toPreserve.paths.toSet())
    }

    @Test
    fun `untouched article not yet in target is translated`() {
        val delta = I18nDelta(
            modifiedArticles = emptyList(),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "hash-v1", "blog/post-3" to "hash-v3")
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1")
        )

        val result = applier.apply()

        assertEquals(setOf("blog/post-3"), result.toTranslate.paths.toSet())
        assertEquals(setOf("blog/post-1"), result.toPreserve.paths.toSet())
    }

    @Test
    fun `idempotence re-run with zero delta touches nothing`() {
        val delta = I18nDelta(
            modifiedArticles = emptyList(),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "hash-v1", "blog/post-2" to "hash-v2")
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1", "blog/post-2")
        )

        val result = applier.apply()

        assertTrue(result.toTranslate.paths.isEmpty())
        assertEquals(2, result.toPreserve.paths.size)
    }

    @Test
    fun `orphaned target files not in source delta are preserved`() {
        val delta = I18nDelta(
            modifiedArticles = emptyList(),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf("blog/post-1" to "hash-v1")
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1", "blog/post-2")
        )

        val result = applier.apply()

        assertTrue(result.toTranslate.paths.isEmpty())
        assertEquals(setOf("blog/post-1", "blog/post-2"), result.toPreserve.paths.toSet())
    }

    @Test
    fun `mixed scenario modified plus untouched plus new plus orphaned`() {
        val delta = I18nDelta(
            modifiedArticles = listOf(
                ArticleModification("blog/post-1", "old", "new", 5)
            ),
            untouchedArticles = emptyList(),
            updatedChecksums = mapOf(
                "blog/post-1" to "new",
                "blog/post-2" to "stable",
                "blog/post-3" to "fresh"
            )
        )
        val applier = I18nDeltaApplier(
            delta,
            existingTargetFiles = setOf("blog/post-1", "blog/post-2", "blog/post-4")
        )

        val result = applier.apply()

        assertEquals(setOf("blog/post-1", "blog/post-3"), result.toTranslate.paths.toSet())
        assertEquals(setOf("blog/post-2", "blog/post-4"), result.toPreserve.paths.toSet())
    }

    @Test
    fun `empty delta with empty target translates nothing`() {
        val delta = I18nDelta(
            modifiedArticles = emptyList(),
            untouchedArticles = emptyList(),
            updatedChecksums = emptyMap()
        )
        val applier = I18nDeltaApplier(delta, existingTargetFiles = emptySet())

        val result = applier.apply()

        assertTrue(result.toTranslate.paths.isEmpty())
        assertTrue(result.toPreserve.paths.isEmpty())
    }
}
