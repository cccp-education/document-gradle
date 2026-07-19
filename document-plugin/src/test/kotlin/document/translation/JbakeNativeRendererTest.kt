package document.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JbakeNativeRendererTest {

    private val parser = AsciiDocParser()
    private val renderer = JbakeNativeRenderer()

    @Test
    fun `renders jbake native header with document title author and date`() {
        val adoc = """
            = Test Article
            @CherOliv
            2020-01-15
            :jbake-type: post
            :jbake-status: published

            Body paragraph.
        """.trimIndent()

        val article = parser.parse(adoc)
        val rendered = renderer.render(article)

        assertTrue(rendered.startsWith("= Test Article"),
            "Expected header to start with document title, got: ${rendered.take(50)}")
        assertTrue(rendered.contains("@CherOliv"),
            "Expected author line, got: ${rendered.take(100)}")
        assertTrue(rendered.contains("2020-01-15"),
            "Expected date line, got: ${rendered.take(100)}")
        assertTrue(rendered.contains(":jbake-type: post"),
            "Expected jbake-type attribute, got: ${rendered.take(200)}")
        assertTrue(rendered.contains(":jbake-status: published"),
            "Expected jbake-status attribute, got: ${rendered.take(200)}")
    }

    @Test
    fun `renders jbake native body blocks after header`() {
        val adoc = """
            = Test Article
            @CherOliv
            2020-01-15
            :jbake-type: post

            == First Section

            Paragraph text.

            [source,bash]
            ----
            echo hello
            ----
        """.trimIndent()

        val article = parser.parse(adoc)
        val rendered = renderer.render(article)

        assertTrue(rendered.contains("== First Section"),
            "Expected heading in body, got: ${rendered.take(200)}")
        assertTrue(rendered.contains("Paragraph text."),
            "Expected paragraph in body, got: ${rendered.take(200)}")
        assertTrue(rendered.contains("[source,bash]"),
            "Expected source block delimiter, got: ${rendered.take(300)}")
        assertTrue(rendered.contains("echo hello"),
            "Expected source content, got: ${rendered.take(300)}")
    }

    @Test
    fun `jbake native roundtrip preserves header and body structure`() {
        val adoc = """
            = Roundtrip Test
            @CherOliv
            2021-03-20
            :jbake-title: Roundtrip Test
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2021-03-20
            :summary: A test article

            == Heading

            Paragraph with text.
        """.trimIndent()

        val article = parser.parse(adoc)
        val rendered = renderer.render(article)
        val reparsed = parser.parse(rendered)

        assertEquals(article.frontmatter.title, reparsed.frontmatter.title)
        assertEquals(article.frontmatter.type, reparsed.frontmatter.type)
        assertEquals(article.frontmatter.status, reparsed.frontmatter.status)
        assertEquals(article.blocks.size, reparsed.blocks.size)
    }

    @Test
    fun `preserves extra jbake attributes like tags and summary`() {
        val adoc = """
            = Article With Tags
            @CherOliv
            2022-06-10
            :jbake-title: Article With Tags
            :jbake-tags: blog, kotlin, test
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2022-06-10
            :summary: A tagged article

            Body.
        """.trimIndent()

        val article = parser.parse(adoc)
        val rendered = renderer.render(article)

        assertTrue(rendered.contains(":jbake-tags: blog, kotlin, test"),
            "Expected jbake-tags preserved, got: ${rendered.take(300)}")
        assertTrue(rendered.contains(":summary: A tagged article"),
            "Expected summary preserved, got: ${rendered.take(300)}")
    }

    @Test
    fun `renders translated title in jbake native header`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter(
                title = "Translated Title",
                date = "2020-01-15",
                type = "post",
                status = "published",
                author = "CherOliv",
                jbakeAttributes = mapOf(
                    "title" to "Original Title",
                    "type" to "post",
                    "status" to "published",
                    "date" to "2020-01-15",
                    "tags" to "blog",
                    "summary" to "Original summary"
                )
            ),
            blocks = listOf(PivotBlock.Paragraph(listOf(PivotInline.Text("Translated body.", true))))
        )

        val rendered = renderer.render(article)

        assertTrue(rendered.contains("= Translated Title"),
            "Expected translated document title, got: ${rendered.take(80)}")
        assertTrue(rendered.contains("@CherOliv"),
            "Expected author preserved, got: ${rendered.take(100)}")
        assertTrue(rendered.contains(":jbake-title: Translated Title"),
            "Expected jbake-title translated, got: ${rendered.take(200)}")
        assertTrue(rendered.contains("Translated body."),
            "Expected translated body, got: ${rendered.take(300)}")
        assertTrue(rendered.contains(":jbake-tags: blog"),
            "Expected tags preserved, got: ${rendered.take(300)}")
    }
}
