package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsciiDocRendererTest {

    private val parser = AsciiDocParser()
    private val renderer = AsciiDocRenderer()

    @Test
    fun `roundtrip preserves article structure`() {
        val original = """title=Test Article
date=2026-06-26
type=page
status=published
~~~~~~

== Heading 1

First paragraph with **bold** and `code` text.

== Heading 2

Second paragraph with https://example.com[link].

* Item 1
* Item 2

1. Ordered 1
2. Ordered 2

[NOTE]
====
Admonition content with *emphasis*.
====
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article, reparsed, "Roundtrip should produce identical PivotArticle")
    }

    @Test
    fun `roundtrip preserves frontmatter`() {
        val original = """title=Frontmatter Test
date=2026-01-15
type=post
status=draft
~~~~~~

== Content
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article.frontmatter, reparsed.frontmatter)
    }

    @Test
    fun `roundtrip preserves table`() {
        val original = """title=Table Test
date=2026-06-26
type=page
status=published
~~~~~~

[cols="1,2,3"]
|===
| Col1 | Col2 | Col3

| A1 | A2 | A3
| B1 | B2 | B3
|===
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article, reparsed)
    }

    @Test
    fun `roundtrip preserves source block`() {
        val original = """title=Source Test
date=2026-06-26
type=page
status=published
~~~~~~

Text before.

[source,java]
----
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
----

Text after.
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article, reparsed)
    }

    @Test
    fun `roundtrip preserves admonition`() {
        val original = """title=Admonition Test
date=2026-06-26
type=page
status=published
~~~~~~

Before.

[WARNING]
====
This is a warning with **important** text.
====

After.
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article, reparsed)
    }

    @Test
    fun `roundtrip preserves inline formatting`() {
        val original = """title=Inline Test
date=2026-06-26
type=page
status=published
~~~~~~

Paragraph with `code`, **bold**, https://example.com[link], and link:page[Page].

Also has plain text.
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)
        assertEquals(article, reparsed)
    }

    @Test
    fun `render output is valid AsciiDoc`() {
        val original = """title=Valid Adoc
date=2026-06-26
type=page
status=published
~~~~~~

== Section

Content here.
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)
        assertTrue(rendered.startsWith("title="))
        assertTrue(rendered.contains("~~~~~~"))
        assertTrue(rendered.contains("== Section"))
        assertTrue(rendered.contains("Content here."))
    }

    @Test
    fun `empty article renders minimal valid document`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter(
                title = "Empty",
                date = "2026-01-01",
                type = "page",
                status = "published"
            ),
            blocks = emptyList()
        )
        val rendered = renderer.render(article)
        assertTrue(rendered.startsWith("title=Empty"))
        assertTrue(rendered.contains("~~~~~~"))
    }

    @Test
    fun `roundtrip preserves plus line continuations`() {
        val original = """title=Continuation Test
date=2026-01-01
type=page
status=published
~~~~~~

depuis le dossier ou est le fichier +
ouvrir un terminal et copier coller +
resultat attendu.
"""
        val article = parser.parse(original)
        val rendered = renderer.render(article)

        assertTrue(rendered.contains("depuis le dossier ou est le fichier +"),
            "Roundtrip should preserve + continuation on first line")
        assertTrue(rendered.contains("ouvrir un terminal et copier coller +"),
            "Roundtrip should preserve + continuation on second line")
        assertTrue(rendered.contains("resultat attendu."),
            "Roundtrip should preserve last line without +")
    }
}
