package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PivotYamlRendererTest {

    private val renderer = PivotYamlRenderer()

    @Test
    fun `renders minimal article with frontmatter and single heading`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter(
                title = "Guide de demarrage rapide",
                date = "2026-04-26",
                type = "page",
                status = "published"
            ),
            blocks = listOf(
                PivotBlock.Heading(level = 2, text = "Guide de demarrage rapide", translatable = true)
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.startsWith("article:"), "YAML must start with article root")
        assertTrue(yaml.contains("frontmatter:"), "YAML must contain frontmatter section")
        assertTrue(yaml.contains("title: \"Guide de demarrage rapide\""), "YAML must contain title")
        assertTrue(yaml.contains("blocks:"), "YAML must contain blocks section")
        assertTrue(yaml.contains("type: heading"), "YAML must contain heading block type")
        assertTrue(yaml.contains("level: 2"), "YAML must contain heading level")
        assertTrue(yaml.contains("translatable: true"), "YAML must contain translatable flag")
    }

    @Test
    fun `renders paragraph with inline text bold code segments`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Paragraph(
                    inline = listOf(
                        PivotInline.Text("avec ", translatable = true),
                        PivotInline.Bold("balenaEtcher", translatable = true),
                        PivotInline.Text(" (recommande) :", translatable = true)
                    )
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: paragraph"), "Must contain paragraph type")
        assertTrue(yaml.contains("inline:"), "Must contain inline section")
        assertTrue(yaml.contains("type: bold"), "Must contain bold inline type")
        assertTrue(yaml.contains("text: \"balenaEtcher\""), "Must contain bold text")
    }

    @Test
    fun `renders source block with language and content`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Source(
                    language = "bash",
                    content = "sudo dd if=img.iso of=/dev/sdX bs=4M"
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: source"), "Must contain source type")
        assertTrue(yaml.contains("language: bash"), "Must contain language")
        assertTrue(yaml.contains("translatable: false"), "Source is non-translatable")
        assertTrue(yaml.contains("sudo dd if=img.iso"), "Must contain source content")
    }

    @Test
    fun `renders hr block as non-translatable`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(PivotBlock.Hr)
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: hr"), "Must contain hr type")
        assertTrue(yaml.contains("translatable: false"), "hr is non-translatable")
    }

    @Test
    fun `renders list block with ordered flag and items`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.ListBlock(
                    ordered = true,
                    items = listOf(
                        listOf(PivotInline.Text("Premiere etape", translatable = true)),
                        listOf(PivotInline.Text("Deuxieme etape", translatable = true))
                    )
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: list"), "Must contain list type")
        assertTrue(yaml.contains("ordered: true"), "Must contain ordered flag")
        assertTrue(yaml.contains("items:"), "Must contain items section")
        assertTrue(yaml.contains("\"Premiere etape\""), "Must contain first item")
        assertTrue(yaml.contains("\"Deuxieme etape\""), "Must contain second item")
    }

    @Test
    fun `renders table with cols header and rows`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Table(
                    cols = "1,2",
                    header = listOf(
                        listOf(PivotInline.Text("Option", translatable = true)),
                        listOf(PivotInline.Text("Description", translatable = true))
                    ),
                    rows = listOf(
                        listOf(
                            listOf(PivotInline.Code("-c", translatable = false)),
                            listOf(PivotInline.Text("Nettoyer le repertoire", translatable = true))
                        )
                    )
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: table"), "Must contain table type")
        assertTrue(yaml.contains("cols: \"1,2\""), "Must contain cols spec")
        assertTrue(yaml.contains("header:"), "Must contain header section")
        assertTrue(yaml.contains("rows:"), "Must contain rows section")
        assertTrue(yaml.contains("type: code"), "Must contain code inline in cell")
        assertTrue(yaml.contains("\"-c\""), "Must contain code text")
    }

    @Test
    fun `renders table with null cols as null`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Table(
                    cols = null,
                    header = listOf(listOf(PivotInline.Text("Col", translatable = true))),
                    rows = emptyList()
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("cols: null"), "Null cols must render as null")
    }

    @Test
    fun `renders admonition with kind and nested blocks`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Admonition(
                    kind = "WARNING",
                    blocks = listOf(
                        PivotBlock.Paragraph(
                            inline = listOf(
                                PivotInline.Text("Cette operation ", translatable = true),
                                PivotInline.Bold("efface toutes les donnees", translatable = true)
                            )
                        )
                    )
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: admonition"), "Must contain admonition type")
        assertTrue(yaml.contains("kind: WARNING"), "Must contain admonition kind")
        assertTrue(yaml.contains("type: paragraph"), "Must contain nested paragraph")
        assertTrue(yaml.contains("type: bold"), "Must contain nested bold")
    }

    @Test
    fun `renders link inline with url and label`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-01-01", "page", "published"),
            blocks = listOf(
                PivotBlock.Paragraph(
                    inline = listOf(
                        PivotInline.Link(
                            url = "https://sourceforge.net/projects/magic-stick/files/",
                            label = "magic-stick sur SourceForge",
                            translatable = true
                        )
                    )
                )
            )
        )

        val yaml = renderer.render(article)

        assertTrue(yaml.contains("type: link"), "Must contain link type")
        assertTrue(yaml.contains("url: \"https://sourceforge.net"), "Must contain link url")
        assertTrue(yaml.contains("label: \"magic-stick sur SourceForge\""), "Must contain link label")
    }
}
