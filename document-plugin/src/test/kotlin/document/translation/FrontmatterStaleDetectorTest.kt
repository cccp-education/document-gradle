package document.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FrontmatterStaleDetectorTest {

    @Nested
    inner class JbakeAttributes {
        @Test
        fun `detects stale jbake-summary when source equals target`() {
            val source = PivotFrontmatter(
                title = "Article FR",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("summary" to "Un resume en francais")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("summary" to "Un resume en francais")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale, "summary identical source/target must be stale")
            assertEquals(setOf("summary"), report.staleKeys)
        }

        @Test
        fun `preserves non-stale jbake-summary when target differs from source`() {
            val source = PivotFrontmatter(
                title = "Article FR",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("summary" to "Un resume en francais")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("summary" to "A short summary in English")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertFalse(report.stale, "summary differs source/target must not be stale")
        }

        @Test
        fun `detects stale jbake-description when source equals target`() {
            val source = PivotFrontmatter(
                title = "Article FR",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("description" to "Une longue description en francais")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "2026-01-01",
                type = "post",
                status = "published",
                jbakeAttributes = mapOf("description" to "Une longue description en francais")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("description"), report.staleKeys)
        }

        @Test
        fun `blank summary is not stale`() {
            val source = PivotFrontmatter(
                title = "Article",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertFalse(report.stale, "blank summary must not be flagged stale")
        }

        @Test
        fun `missing summary in target is not stale`() {
            val source = PivotFrontmatter(
                title = "Article",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = emptyMap()
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertFalse(report.stale, "missing summary key in target must not be stale (nothing to fix)")
        }
    }

    @Nested
    inner class AsciidocAttributes {
        @Test
        fun `detects stale asciidoc summary when source equals target`() {
            val source = PivotFrontmatter(
                title = "Article FR",
                date = "2026-01-01",
                type = "post",
                status = "published",
                asciidocAttributes = mapOf("summary" to "ajouter un dossier au classpath")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "2026-01-01",
                type = "post",
                status = "published",
                asciidocAttributes = mapOf("summary" to "ajouter un dossier au classpath")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("summary"), report.staleKeys)
        }

        @Test
        fun `detects stale asciidoc description when source equals target`() {
            val source = PivotFrontmatter(
                title = "Article FR",
                date = "",
                type = "",
                status = "",
                asciidocAttributes = mapOf("description" to "Description FR")
            )
            val target = PivotFrontmatter(
                title = "Article EN",
                date = "",
                type = "",
                status = "",
                asciidocAttributes = mapOf("description" to "Description FR")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("description"), report.staleKeys)
        }
    }

    @Nested
    inner class TitleAttribute {
        @Test
        fun `detects stale title when source equals target`() {
            val source = PivotFrontmatter(
                title = "Titre en francais",
                date = "",
                type = "",
                status = ""
            )
            val target = PivotFrontmatter(
                title = "Titre en francais",
                date = "",
                type = "",
                status = ""
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("title"), report.staleKeys)
        }

        @Test
        fun `title differs source target is not stale`() {
            val source = PivotFrontmatter(
                title = "Titre en francais",
                date = "",
                type = "",
                status = ""
            )
            val target = PivotFrontmatter(
                title = "Title in English",
                date = "",
                type = "",
                status = ""
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertFalse(report.stale)
        }
    }

    @Nested
    inner class MultipleStaleKeys {
        @Test
        fun `detects multiple stale keys simultaneously`() {
            val source = PivotFrontmatter(
                title = "Titre FR",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR", "description" to "Description FR")
            )
            val target = PivotFrontmatter(
                title = "Titre FR",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR", "description" to "Description FR")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("title", "summary", "description"), report.staleKeys)
        }

        @Test
        fun `mixed stale and fresh keys reports only stale ones`() {
            val source = PivotFrontmatter(
                title = "Titre FR",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR", "description" to "Description FR")
            )
            val target = PivotFrontmatter(
                title = "Title EN",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR", "description" to "Description EN")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertTrue(report.stale)
            assertEquals(setOf("summary"), report.staleKeys)
        }
    }

    @Nested
    inner class FullyTranslated {
        @Test
        fun `nothing stale when all translatable attributes differ`() {
            val source = PivotFrontmatter(
                title = "Titre FR",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Resume FR", "description" to "Description FR"),
                asciidocAttributes = mapOf("summary" to "AsciiSummary FR")
            )
            val target = PivotFrontmatter(
                title = "Title EN",
                date = "",
                type = "",
                status = "",
                jbakeAttributes = mapOf("summary" to "Summary EN", "description" to "Description EN"),
                asciidocAttributes = mapOf("summary" to "AsciiSummary EN")
            )
            val report = FrontmatterStaleDetector.detect(source, target)
            assertFalse(report.stale)
            assertEquals(emptySet<String>(), report.staleKeys)
        }
    }
}