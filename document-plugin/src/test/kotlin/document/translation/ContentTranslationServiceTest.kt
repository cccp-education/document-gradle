package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTranslationServiceTest {

    private val parser = AsciiDocParser()
    private val renderer = AsciiDocRenderer()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `translate replaces translatable text in article`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Bonjour
date=2026-06-26
type=page
status=published
~~~~~~

== Salut

Ceci est un **texte** en francais.
""")
        val result = service.translateArticle(article, "fr", "en")

        assertEquals("Bonjour [EN]", result.frontmatter.title)
        val heading = result.blocks[0] as PivotBlock.Heading
        assertEquals("Salut [EN]", heading.text)
        val para = result.blocks[1] as PivotBlock.Paragraph
        assertTrue(para.inline[0].toString().contains("[EN]"))
    }

    @Test
    fun `non-translatable inline is not translated`() {
        val fake = FakeTranslationService(" [TR]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

Text with `code` and **bold**.
""")
        val result = service.translateArticle(article, "fr", "en")

        val para = result.blocks[0] as PivotBlock.Paragraph
        val code = para.inline[1] as PivotInline.Code
        assertEquals("code", code.text)
        val bold = para.inline[3] as PivotInline.Bold
        assertEquals("bold [TR]", bold.text)
    }

    @Test
    fun `source blocks are never translated`() {
        val fake = FakeTranslationService(" [X]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Source
date=2026-06-26
type=page
status=published
~~~~~~

[source,java]
----
public class Hello {}
----
""")
        val result = service.translateArticle(article, "fr", "en")

        val source = result.blocks[0] as PivotBlock.Source
        assertEquals("public class Hello {}", source.content)
    }

    @Test
    fun `translates heading text`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

== Introduction
=== Sous-section
""")
        val result = service.translateArticle(article, "fr", "en")

        assertEquals("Introduction [EN]", (result.blocks[0] as PivotBlock.Heading).text)
        assertEquals("Sous-section [EN]", (result.blocks[1] as PivotBlock.Heading).text)
    }

    @Test
    fun `translates list items`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Liste
date=2026-06-26
type=page
status=published
~~~~~~

* Item un
* Item deux

1. Premier
2. Second
""")
        val result = service.translateArticle(article, "fr", "en")

        val ul = result.blocks[0] as PivotBlock.ListBlock
        assertEquals("Item un [EN]", (ul.items[0][0] as PivotInline.Text).text)
        assertEquals("Item deux [EN]", (ul.items[1][0] as PivotInline.Text).text)
        val ol = result.blocks[1] as PivotBlock.ListBlock
        assertEquals("Premier [EN]", (ol.items[0][0] as PivotInline.Text).text)
    }

    @Test
    fun `translates table cells`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Tableau
date=2026-06-26
type=page
status=published
~~~~~~

[cols="1,2"]
|===
| Nom | Valeur

| Un | 1
| Deux | 2
|===
""")
        val result = service.translateArticle(article, "fr", "en")

        val table = result.blocks[0] as PivotBlock.Table
        assertEquals("Nom [EN]", (table.header[0][0] as PivotInline.Text).text)
        assertEquals("Valeur [EN]", (table.header[1][0] as PivotInline.Text).text)
    }

    @Test
    fun `translates admonition content`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Note
date=2026-06-26
type=page
status=published
~~~~~~

[NOTE]
====
Texte important avec **gras**.
====
""")
        val result = service.translateArticle(article, "fr", "en")

        val adm = result.blocks[0] as PivotBlock.Admonition
        val para = adm.blocks[0] as PivotBlock.Paragraph
        assertTrue(para.inline[0].toString().contains("[EN]"))
    }

    @Test
    fun `translate on files processes all adoc in directory`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val langDir = tempDir.resolve("en").also { it.mkdirs() }
        langDir.resolve("article1.adoc").writeText("""title=Article 1
date=2026-06-26
type=page
status=published
~~~~~~

Premier article.
""")
        langDir.resolve("article2.adoc").writeText("""title=Article 2
date=2026-06-26
type=page
status=published
~~~~~~

Second article.
""")
        langDir.resolve("data.txt").writeText("not an adoc")

        val result = service.translate(langDir, "fr", "en")

        assertEquals(2, result.filesTranslated.size)
        assertTrue(result.success)
        assertTrue(langDir.resolve("article1.adoc").readText().contains("[EN]"))
        assertTrue(langDir.resolve("article2.adoc").readText().contains("[EN]"))
    }

    @Test
    fun `non-adoc files are skipped by translate`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val langDir = tempDir.resolve("en").also { it.mkdirs() }
        langDir.resolve("data.json").writeText("{}")
        langDir.resolve("image.png").writeText("fake")

        val result = service.translate(langDir, "fr", "en")

        assertEquals(0, result.filesTranslated.size)
    }

    @Test
    fun `translation failure falls back to original text`() {
        val failing = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult {
                return TranslationResult.Failure("API down")
            }
        }
        val service = ContentTranslationService(failing, parser, renderer)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

Hello world.
""")
        val result = service.translateArticle(article, "fr", "en")

        assertEquals("Test", result.frontmatter.title)
        val para = result.blocks[0] as PivotBlock.Paragraph
        assertEquals("Hello world.", (para.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `plantuml source block is preserved when no adapter is wired`() {
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

[plantuml]
----
@startuml
actor "Utilisateur" as User
User -> Service: Requête
@enduml
----
""")
        val result = service.translateArticle(article, "fr", "en")

        val source = result.blocks[0] as PivotBlock.Source
        assertEquals("plantuml", source.language)
        assertTrue(source.content.contains("Utilisateur"))
    }

    @Test
    fun `plantuml source block is translated through adapter when wired`() {
        val fake = FakeTranslationService(" [EN]")
        val adapter = document.translation.plantuml.PlantUmlTranslationAdapter(fake)
        val service = ContentTranslationService(fake, parser, renderer, plantUmlAdapter = adapter)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

[plantuml]
----
@startuml
actor "Utilisateur" as User
User -> Service: Requête
@enduml
----
""")
        val result = service.translateArticle(article, "fr", "en")

        val source = result.blocks[0] as PivotBlock.Source
        assertEquals("plantuml", source.language)
        assertTrue(source.content.contains("Utilisateur [EN]"))
    }

    @Test
    fun `non-plantuml source block is not dispatched to adapter`() {
        val fake = FakeTranslationService(" [EN]")
        val adapter = document.translation.plantuml.PlantUmlTranslationAdapter(fake)
        val service = ContentTranslationService(fake, parser, renderer, plantUmlAdapter = adapter)

        val article = parser.parse("""title=Test
date=2026-06-26
type=page
status=published
~~~~~~

[source,java]
----
public class Hello {}
----
""")
        val result = service.translateArticle(article, "fr", "en")

        val source = result.blocks[0] as PivotBlock.Source
        assertEquals("java", source.language)
        assertEquals("public class Hello {}", source.content)
    }

    @Test
    fun `concurrent translation of articles with plantuml blocks is thread-safe`() {
        val fake = FakeTranslationService(" [EN]")
        val adapter = document.translation.plantuml.PlantUmlTranslationAdapter(fake)
        val service = ContentTranslationService(fake, parser, renderer, plantUmlAdapter = adapter)

        val langDir = tempDir.resolve("i18n/en")
        langDir.mkdirs()
        for (i in 1..5) {
            langDir.resolve("article-$i.adoc").writeText("""= Article $i
@CherOliv
2026-07-21
:jbake-type: post

[plantuml]
----
@startuml
class "Service$i"
@enduml
----
""")
        }

        val result = service.translate(langDir, "fr", "en")

        assertEquals(5, result.filesTranslated.size)
        assertEquals(0, result.errors.size)
        for (i in 1..5) {
            val content = langDir.resolve("article-$i.adoc").readText()
            assertTrue(content.contains("\"Service$i [EN]\""), "article $i label translated")
        }
    }
}

class FakeTranslationService(private val suffix: String) : TranslationService {
    override fun translate(request: TranslationRequest): TranslationResult {
        return TranslationResult.Success(request.sourceText + suffix)
    }
}
