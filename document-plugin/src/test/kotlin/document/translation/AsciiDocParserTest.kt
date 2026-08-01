package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AsciiDocParserTest {

    private val parser = AsciiDocParser()

    @Test
    fun `parses frontmatter header before tilde separator`() {
        val adoc = """
            title=Guide de demarrage rapide
            date=2026-04-26
            type=page
            status=published
            ~~~~~~

            == Guide de demarrage rapide
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals("Guide de demarrage rapide", article.frontmatter.title)
        assertEquals("2026-04-26", article.frontmatter.date)
        assertEquals("page", article.frontmatter.type)
        assertEquals("published", article.frontmatter.status)
    }

    @Test
    fun `parses level 2 heading`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            == Guide de demarrage rapide
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val heading = article.blocks[0] as PivotBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("Guide de demarrage rapide", heading.text)
        assertTrue(heading.translatable)
    }

    @Test
    fun `parses level 3 and level 4 headings`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            == Title 2

            === Title 3

            ==== Title 4
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(3, article.blocks.size)
        assertEquals(2, (article.blocks[0] as PivotBlock.Heading).level)
        assertEquals(3, (article.blocks[1] as PivotBlock.Heading).level)
        assertEquals(4, (article.blocks[2] as PivotBlock.Heading).level)
    }

    @Test
    fun `parses simple paragraph as single text inline`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Ce guide vous permet d'etre operationnel avec Magic Stick en quelques minutes.
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        assertEquals(1, para.inline.size)
        val text = para.inline[0] as PivotInline.Text
        assertEquals("Ce guide vous permet d'etre operationnel avec Magic Stick en quelques minutes.", text.text)
        assertTrue(text.translatable)
    }

    @Test
    fun `parses paragraph with bold segment`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            avec **balenaEtcher** (recommande) :
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        assertEquals(3, para.inline.size)
        assertEquals("avec ", (para.inline[0] as PivotInline.Text).text)
        assertEquals("balenaEtcher", (para.inline[1] as PivotInline.Bold).text)
        assertEquals(" (recommande) :", (para.inline[2] as PivotInline.Text).text)
    }

    @Test
    fun `parses paragraph with inline code`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Verifiez le device avec `lsblk` avant.
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        val segments = para.inline
        assertEquals(3, segments.size)
        assertEquals("Verifiez le device avec ", (segments[0] as PivotInline.Text).text)
        assertEquals("lsblk", (segments[1] as PivotInline.Code).text)
        assertEquals(" avant.", (segments[2] as PivotInline.Text).text)
    }

    @Test
    fun `parses paragraph with link inline`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Pour le guide complet avec balenaEtcher et dd, consultez link:flash-guide.html[Guide d'installation et flashage USB].
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        assertEquals(3, para.inline.size)
        assertEquals("Pour le guide complet avec balenaEtcher et dd, consultez ", (para.inline[0] as PivotInline.Text).text)
        val link = para.inline[1] as PivotInline.Link
        assertEquals("flash-guide.html", link.url)
        assertEquals("Guide d'installation et flashage USB", link.label)
        assertEquals(".", (para.inline[2] as PivotInline.Text).text)
    }

    @Test
    fun `parses unordered list with single link item`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            * https://sourceforge.net/projects/magic-stick/files/[magic-stick sur SourceForge]
        """.trimIndent()

        val article = parser.parse(adoc)

        val list = article.blocks[0] as PivotBlock.ListBlock
        assertEquals(false, list.ordered)
        assertEquals(1, list.items.size)
        val link = list.items[0][0] as PivotInline.Link
        assertEquals("https://sourceforge.net/projects/magic-stick/files/", link.url)
        assertEquals("magic-stick sur SourceForge", link.label)
    }

    @Test
    fun `parses ordered list with multiple items`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            . Telechargez https://balena.io/etcher[balenaEtcher]
            . **Flash from file** → selectionnez le fichier ISO `magic-stick_{version}.iso`
            . **Select target** → votre cle USB
            . **Flash!**
        """.trimIndent()

        val article = parser.parse(adoc)

        val list = article.blocks[0] as PivotBlock.ListBlock
        assertEquals(true, list.ordered)
        assertEquals(4, list.items.size)
        assertEquals(2, list.items[0].size)
        assertEquals("Telechargez ", (list.items[0][0] as PivotInline.Text).text)
        assertEquals("balenaEtcher", (list.items[0][1] as PivotInline.Link).label)
        assertEquals(3, list.items[1].size)
        assertEquals("Flash from file", (list.items[1][0] as PivotInline.Bold).text)
    }

    @Test
    fun `parses source block with language`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [source,bash]
            ----
            sudo dd if=magic-stick_{version}.iso of=/dev/sdX bs=4M status=progress && sync
            ----
        """.trimIndent()

        val article = parser.parse(adoc)

        val src = article.blocks[0] as PivotBlock.Source
        assertEquals("bash", src.language)
        assertTrue(src.content.contains("sudo dd if=magic-stick_{version}.iso"))
    }

    @Test
    fun `parses source block without language`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [source]
            ----
            /unicorn/home
            /unicorn/usr
            ----
        """.trimIndent()

        val article = parser.parse(adoc)

        val src = article.blocks[0] as PivotBlock.Source
        assertEquals("", src.language)
        assertTrue(src.content.contains("/unicorn/home"))
    }

    @Test
    fun `parses admonition with kind and paragraph`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [WARNING]
            ====
            Cette operation **efface toutes les donnees** sur la cle USB. Verifiez le device avec `lsblk` avant.
            ====
        """.trimIndent()

        val article = parser.parse(adoc)

        val adm = article.blocks[0] as PivotBlock.Admonition
        assertEquals("WARNING", adm.kind)
        assertEquals(1, adm.blocks.size)
        val para = adm.blocks[0] as PivotBlock.Paragraph
        assertEquals(5, para.inline.size)
        assertEquals("efface toutes les donnees", (para.inline[1] as PivotInline.Bold).text)
    }

    @Test
    fun `parses inline admonition without block delimiters`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [NOTE]
            Le fine-tune n'est pas un remplacement du RAG. Il reste pertinent pour les donnees qui changent souvent.

            == Heading after
        """.trimIndent()

        val article = parser.parse(adoc)

        val adm = article.blocks[0] as PivotBlock.Admonition
        assertEquals("NOTE", adm.kind)
        assertEquals(1, adm.blocks.size)
        val para = adm.blocks[0] as PivotBlock.Paragraph
        assertTrue(para.inline.any { it is PivotInline.Text && it.text.contains("remplacement") })
        val heading = article.blocks[1] as PivotBlock.Heading
        assertEquals("Heading after", heading.text)
    }

    @Test
    fun `parses hr separator`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            ---
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(PivotBlock.Hr, article.blocks[0])
    }

    @Test
    fun `parses table with cols spec header and rows`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [cols="1,2,3"]
            |===
            | Partition | Taille | Role

            | System A (lecture seule)
            | ~8 Go
            | Systeme live Xubuntu actif (boote par defaut)
            |===
        """.trimIndent()

        val article = parser.parse(adoc)

        val table = article.blocks[0] as PivotBlock.Table
        assertEquals("1,2,3", table.cols)
        assertEquals(3, table.header.size)
        assertEquals("Partition", (table.header[0][0] as PivotInline.Text).text)
        assertEquals(1, table.rows.size)
        assertEquals(3, table.rows[0].size)
        assertEquals("System A (lecture seule)", (table.rows[0][0][0] as PivotInline.Text).text)
        assertEquals("~8 Go", (table.rows[0][1][0] as PivotInline.Text).text)
    }

    @Test
    fun `parses table without cols spec as null cols`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            |===
            | Partition | Device | Taille

            | System A
            | `/dev/sdX1`
            | ~8 Go
            |===
        """.trimIndent()

        val article = parser.parse(adoc)

        val table = article.blocks[0] as PivotBlock.Table
        assertEquals(null, table.cols)
        assertEquals(3, table.header.size)
        assertEquals(1, table.rows.size)
        val code = table.rows[0][1][0] as PivotInline.Code
        assertEquals("/dev/sdX1", code.text)
    }

    @Test
    fun `parses table cell with inline code and text`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [cols="1,3"]
            |===
            | Option | Description

            | `-c`, `--clean`
            | Nettoyer le repertoire de build avant de construire
            |===
        """.trimIndent()

        val article = parser.parse(adoc)

        val table = article.blocks[0] as PivotBlock.Table
        val cell = table.rows[0][0]
        assertEquals(3, cell.size)
        assertEquals("-c", (cell[0] as PivotInline.Code).text)
        assertEquals(", ", (cell[1] as PivotInline.Text).text)
        assertEquals("--clean", (cell[2] as PivotInline.Code).text)
    }

    @Test
    fun `parses numbered ordered list with digit prefix`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            1. Laissez la cle USB inseree
            2. Redemarrez le PC
            3. Accedez au BIOS/UEFI
        """.trimIndent()

        val article = parser.parse(adoc)

        val list = article.blocks[0] as PivotBlock.ListBlock
        assertEquals(true, list.ordered)
        assertEquals(3, list.items.size)
        assertEquals("Laissez la cle USB inseree", (list.items[0][0] as PivotInline.Text).text)
        assertEquals("Redemarrez le PC", (list.items[1][0] as PivotInline.Text).text)
        assertEquals("Accedez au BIOS/UEFI", (list.items[2][0] as PivotInline.Text).text)
    }

    @Test
    fun `parses level 4 heading with four equal signs and text`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            ==== Fonctionnement
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val heading = article.blocks[0] as PivotBlock.Heading
        assertEquals(4, heading.level)
        assertEquals("Fonctionnement", heading.text)
    }

    @Test
    fun `parses source block closed by four equal signs instead of dashes`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [source,bash]
            ----
            sudo scripts/flash.sh /dev/sdX
            ====

            ==== Fonctionnement
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(2, article.blocks.size)
        val src = article.blocks[0] as PivotBlock.Source
        assertEquals("bash", src.language)
        assertEquals("sudo scripts/flash.sh /dev/sdX", src.content)
        val heading = article.blocks[1] as PivotBlock.Heading
        assertEquals(4, heading.level)
        assertEquals("Fonctionnement", heading.text)
    }

    @Test
    fun `skips stray four-dashes line without infinite loop`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Premier paragraphe.

            ----

            Deuxieme paragraphe.
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(2, article.blocks.size)
        assertTrue(article.blocks[0] is PivotBlock.Paragraph)
        assertTrue(article.blocks[1] is PivotBlock.Paragraph)
    }

    @Test
    fun `parses source block without closing delimiter reaches end of input`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [source,bash]
            ----
            sudo scripts/unclosed.sh
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val src = article.blocks[0] as PivotBlock.Source
        assertEquals("sudo scripts/unclosed.sh", src.content)
    }

    @Test
    fun `parses table cell spanning multiple lines as single cell`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [cols="1,3"]
            |===
            | Option | Description

            | `-c`, `--clean`
            | Nettoyer le repertoire
            de build avant de construire
            |===
        """.trimIndent()

        val article = parser.parse(adoc)

        val table = article.blocks[0] as PivotBlock.Table
        assertEquals(2, table.rows[0].size)
        val descriptionCell = table.rows[0][1]
        val textSegment = descriptionCell.find { it is PivotInline.Text } as PivotInline.Text
        assertTrue(textSegment.text.contains("Nettoyer le repertoire"))
        assertTrue(textSegment.text.contains("de build avant de construire"))
        assertEquals("Nettoyer le repertoire de build avant de construire", textSegment.text)
    }

    @Test
    fun `parses nested unordered list with double asterisk sub-items`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            * item parent
            ** sous-item A
            ** sous-item B
            * autre parent
        """.trimIndent()

        val article = parser.parse(adoc)

        val list = article.blocks[0] as PivotBlock.ListBlock
        assertEquals(false, list.ordered)
        assertEquals(4, list.items.size)
        assertEquals("item parent", (list.items[0][0] as PivotInline.Text).text)
        assertEquals("sous-item A", (list.items[1][0] as PivotInline.Text).text)
        assertEquals("sous-item B", (list.items[2][0] as PivotInline.Text).text)
        assertEquals("autre parent", (list.items[3][0] as PivotInline.Text).text)
    }

    @Test
    fun `parses plantuml block as source-like pass-through`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [plantuml]
            ----
            @startuml
            A --> B
            @enduml
            ----
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val block = article.blocks[0] as PivotBlock.Source
        assertEquals("plantuml", block.language)
        assertTrue(block.content.contains("@startuml"))
        assertTrue(block.content.contains("A --> B"))
        assertTrue(block.content.contains("@enduml"))
    }

    @Test
    fun `parses plantuml block delimited by four dots`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            == Architecture

            [plantuml]
            ....
            @startuml
            package "Core" {
              [Validator] as V
            }
            @enduml
            ....

            == Methodologie

            Texte apres le bloc.
        """.trimIndent()

        val article = parser.parse(adoc)

        val src = article.blocks.filterIsInstance<PivotBlock.Source>().first()
        assertEquals("plantuml", src.language)
        assertTrue(src.content.contains("@startuml"))
        assertTrue(src.content.contains("[Validator] as V"))
        assertTrue(src.content.contains("@enduml"))
        assertTrue(article.blocks.any { it is PivotBlock.Heading && it.text == "Methodologie" },
            "expected heading after the 4-dot plantuml block, got: ${article.blocks}")
    }

    @Test
    fun `parses 4-dot plantuml block without swallowing following content`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            [plantuml]
            ....
            @startuml
            A --> B
            @enduml
            ....

            == Section suivante

            == Section encore apres
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(3, article.blocks.size)
        assertTrue(article.blocks[0] is PivotBlock.Source)
        assertTrue(article.blocks[1] is PivotBlock.Heading && (article.blocks[1] as PivotBlock.Heading).text == "Section suivante")
        assertTrue(article.blocks[2] is PivotBlock.Heading && (article.blocks[2] as PivotBlock.Heading).text == "Section encore apres")
    }

    @Test
    fun `parses source block with block title between header and delimiter`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            == Probleme

            [source,html]
            .Structure HTML initiale
            ----
            <section id="home" class="hero-section">
            </section>
            ----

            == Conclusion

            Texte final.
        """.trimIndent()

        val article = parser.parse(adoc)

        val src = article.blocks.filterIsInstance<PivotBlock.Source>().first()
        assertEquals("html", src.language)
        assertTrue(src.content.contains("hero-section"))
        assertTrue(article.blocks.any { it is PivotBlock.Heading && it.text == "Conclusion" },
            "expected heading after titled source block, got: ${article.blocks}")
    }

    @Test
    fun `parses multiple blocks in sequence`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            == Premier titre

            Premier paragraphe.

            === Deuxieme titre

            * item un
            * item deux
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(4, article.blocks.size)
        assertEquals("Premier titre", (article.blocks[0] as PivotBlock.Heading).text)
        assertTrue(article.blocks[1] is PivotBlock.Paragraph)
        assertEquals("Deuxieme titre", (article.blocks[2] as PivotBlock.Heading).text)
        val list = article.blocks[3] as PivotBlock.ListBlock
        assertEquals(2, list.items.size)
    }

    @Test
    fun `parses jbake native header with document title and jbake attributes`() {
        val adoc = """
            = Groovy: Caractères ASCII
            @CherOliv
            2019-07-10
            :jbake-title: Groovy: Caractères ASCII
            :jbake-tags: blog, Groovy, ASCII, string, char
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2019-07-10
            :summary: du groovy, des boucles et de la manipulation de code ascii

            Voici un bout de code fonctionnel en Groovy.
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals("Groovy: Caractères ASCII", article.frontmatter.title)
        assertEquals("2019-07-10", article.frontmatter.date)
        assertEquals("post", article.frontmatter.type)
        assertEquals("published", article.frontmatter.status)
    }

    @Test
    fun `parses jbake native body blocks after header section`() {
        val adoc = """
            = Test Article
            @Author
            2020-01-15
            :jbake-type: post
            :jbake-status: published

            == First Section

            First paragraph text.

            [source,bash]
            ----
            echo hello
            ----

            Second paragraph.
        """.trimIndent()

        val article = parser.parse(adoc)

        assertTrue(article.blocks.size >= 4,
            "Expected at least 4 blocks, got ${article.blocks.size}: ${article.blocks}")
        val heading = article.blocks[0] as PivotBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("First Section", heading.text)
        assertTrue(article.blocks[1] is PivotBlock.Paragraph)
        val src = article.blocks[2] as PivotBlock.Source
        assertEquals("bash", src.language)
        assertTrue(src.content.contains("echo hello"))
        assertTrue(article.blocks[3] is PivotBlock.Paragraph)
    }

    @Test
    fun `parses markdown fenced code block as source block`() {
        val adoc = """
            = Gradle cli: surcharger un paramètre
            @CherOliv
            2020-09-22
            :jbake-type: post
            :jbake-status: published

            ===== Surcharger un parametre par la ligne de commande :

            ```
            $ ./gradlew -Pparam_component=CUSTOM
            ```
        """.trimIndent()

        val article = parser.parse(adoc)

        val blocks = article.blocks
        assertTrue(blocks.any { it is PivotBlock.Source }, "expected a Source block for fenced markdown code, got: $blocks")
        val src = blocks.filterIsInstance<PivotBlock.Source>().first()
        assertTrue(src.content.contains("./gradlew -Pparam_component=CUSTOM"), "expected code content preserved, got: ${src.content}")
        assertTrue(blocks.none { it is PivotBlock.Paragraph && (it.inline.joinToString("") { if (it is PivotInline.Text) it.text else "" }).contains("```") },
            "fenced markers must not leak into paragraph text")
    }

    @Test
    fun `parses paragraph with plus line continuation as LineBreak inline`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            depuis le dossier ou est le fichier +
            ouvrir un terminal et copier coller pour executer le script +
            resultat attendu.
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        val lineBreaks = para.inline.filterIsInstance<PivotInline.LineBreak>()
        assertEquals(2, lineBreaks.size, "Expected 2 LineBreak inlines for two + continuations")
        assertTrue(para.inline.any { it is PivotInline.Text && it.text.contains("depuis le dossier") })
        assertTrue(para.inline.any { it is PivotInline.Text && it.text.contains("ouvrir un terminal") })
        assertTrue(para.inline.any { it is PivotInline.Text && it.text.contains("resultat attendu") })
    }

    @Test
    fun `parses paragraph with single plus at end of last line as literal text`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            une seule ligne avec un plus a la fin +
        """.trimIndent()

        val article = parser.parse(adoc)

        val para = article.blocks[0] as PivotBlock.Paragraph
        val lineBreaks = para.inline.filterIsInstance<PivotInline.LineBreak>()
        assertEquals(0, lineBreaks.size, "Single line with + at end should not produce LineBreak")
        val text = para.inline.filterIsInstance<PivotInline.Text>().joinToString("") { it.text }
        assertTrue(text.contains("plus a la fin +"), "Trailing + on last line should be preserved as literal text")
    }

    @Test
    fun `parses description list with link terms and text definitions`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            https://undraw.co/[undraw.co]:: Open source illustrations.
            https://absurd.design/[absurd.design]:: Absurd illustrations that make sense.
        """.trimIndent()

        val article = parser.parse(adoc)

        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(2, dl.items.size)
        val item0 = dl.items[0]
        assertEquals("undraw.co", (item0.term[0] as PivotInline.Link).label)
        assertEquals("Open source illustrations.", (item0.definition[0] as PivotInline.Text).text)
        val item1 = dl.items[1]
        assertEquals("absurd.design", (item1.term[0] as PivotInline.Link).label)
        assertEquals("Absurd illustrations that make sense.", (item1.definition[0] as PivotInline.Text).text)
        assertTrue(dl.translatable)
    }

    @Test
    fun `parses description list with plain text terms`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Tools:: A collection of useful tools.
            Colors:: The super fast color palette generator.
        """.trimIndent()

        val article = parser.parse(adoc)

        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(2, dl.items.size)
        assertEquals("Tools", (dl.items[0].term[0] as PivotInline.Text).text)
        assertEquals("A collection of useful tools.", (dl.items[0].definition[0] as PivotInline.Text).text)
        assertEquals("Colors", (dl.items[1].term[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses description list with multi-word terms`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            Google Material Design:: Google's design system for unified interfaces.
            Apple Human Interface Guidelines:: Apple's recommendations for iOS and macOS.
        """.trimIndent()

        val article = parser.parse(adoc)

        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(2, dl.items.size)
        assertEquals("Google Material Design", (dl.items[0].term[0] as PivotInline.Text).text)
        assertEquals("Apple Human Interface Guidelines", (dl.items[1].term[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses description list with empty definition`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            term::
        """.trimIndent()

        val article = parser.parse(adoc)

        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(1, dl.items.size)
        assertEquals("term", (dl.items[0].term[0] as PivotInline.Text).text)
        assertEquals(0, dl.items[0].definition.size)
    }

    @Test
    fun `parses block macro image`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            image::diagram.png[Architecture diagram]
        """.trimIndent()

        val article = parser.parse(adoc)

        val macro = article.blocks[0] as PivotBlock.BlockMacro
        assertEquals("image", macro.name)
        assertEquals("diagram.png", macro.target)
        assertEquals("Architecture diagram", macro.attributes)
        assertEquals(false, macro.translatable)
    }

    @Test
    fun `parses block macro video`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            video::iSWjmVcfQGg[youtube]
        """.trimIndent()

        val article = parser.parse(adoc)

        val macro = article.blocks[0] as PivotBlock.BlockMacro
        assertEquals("video", macro.name)
        assertEquals("iSWjmVcfQGg", macro.target)
        assertEquals("youtube", macro.attributes)
    }

    @Test
    fun `parses block macro without attributes`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            image::screenshot.png
        """.trimIndent()

        val article = parser.parse(adoc)

        val macro = article.blocks[0] as PivotBlock.BlockMacro
        assertEquals("image", macro.name)
        assertEquals("screenshot.png", macro.target)
        assertEquals("", macro.attributes)
    }

    @Test
    fun `description list items are not merged into a single paragraph`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            https://a.com[label]:: First definition.
            https://b.com[label]:: Second definition.
            https://c.com[label]:: Third definition.
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(3, dl.items.size)
    }

    @Test
    fun `block macro lines are not merged into a single paragraph`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            image::a.png[First]
            image::b.png[Second]
            image::c.png[Third]
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(3, article.blocks.size)
        assertTrue(article.blocks.all { it is PivotBlock.BlockMacro })
    }

    @Test
    fun `description list followed by paragraph are separate blocks`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            term:: definition

            A regular paragraph after the description list.
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(2, article.blocks.size)
        assertTrue(article.blocks[0] is PivotBlock.DescriptionList)
        assertTrue(article.blocks[1] is PivotBlock.Paragraph)
    }

    @Test
    fun `comment line with double colon is not a description list item`() {
        val adoc = """
            title=Test
            date=2026-01-01
            type=page
            status=published
            ~~~~~~

            // this is a comment with :: inside
            real:: definition
        """.trimIndent()

        val article = parser.parse(adoc)

        assertEquals(1, article.blocks.size)
        val dl = article.blocks[0] as PivotBlock.DescriptionList
        assertEquals(1, dl.items.size)
        assertEquals("real", (dl.items[0].term[0] as PivotInline.Text).text)
    }
}
