package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeTranslationAdapterTest {

    private val adapter = TreeTranslationAdapter(
        translationService = NumberedFakeTranslationService(),
    )

    @Test
    fun `round trips a well-formed article with numbered fake`() {
        val input = """
            = Titre de l'article
            @cheroliv
            2025-01-01
            :jbake-title: Titre de l'article
            :jbake-tags: test
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2025-01-01
            :summary: Un résumé à traduire.
            :revdate: 2025-01-01

            == Premier paragraphe

            Ceci est un paragraphe en français.

            [NOTE]
            ====
            Ceci est une note.
            ====

            * Premier élément
            * Second élément

            == Code

            [source,bash]
            ----
            echo "ne pas traduire"
            ----
        """.trimIndent()

        val result = adapter.translate(input, "fr", "en")

        assertTrue(result.contains("= Titre de l'article [EN]"), "title translated")
        assertTrue(result.contains(":jbake-title: Titre de l'article [EN]"), "jbake title translated")
        assertTrue(result.contains(":summary: Un résumé à traduire. [EN]"), "summary translated")
        assertTrue(result.contains("== Premier paragraphe [EN]"), "heading translated")
        assertTrue(result.contains("Ceci est un paragraphe en français. [EN]"), "paragraph translated")
        assertTrue(result.contains("Ceci est une note. [EN]"), "admonition translated")
        assertTrue(result.contains("* Premier élément [EN]"), "list item translated")
        assertTrue(result.contains("* Second élément [EN]"), "second list item translated")
        assertTrue(result.contains("echo \"ne pas traduire\""), "source code preserved")
    }

    @Test
    fun `preserves non-translatable content by construction`() {
        val input = """
            = Titre
            @cheroliv
            2025-01-01
            :jbake-title: Titre
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2025-01-01
            :revdate: 2025-01-01

            Texte avec du code `sudo dd` et un lien https://exemple.org[Exemple].

            [plantuml, ma-sequence, svg]
            ----
            @startuml
            actor User
            @enduml
            ----

            Un paragraphe final.
        """.trimIndent()

        val result = adapter.translate(input, "fr", "en")

        assertTrue(result.contains("`sudo dd`"), "inline code preserved")
        assertTrue(result.contains("https://exemple.org[Exemple [EN]]"), "link URL preserved with translated label")
        assertTrue(result.contains("[plantuml, ma-sequence, svg]"), "plantuml header preserved")
        assertTrue(result.contains("actor User"), "plantuml content preserved")
        assertTrue(result.contains("Un paragraphe final. [EN]"), "trailing paragraph translated")
    }

    @Test
    fun `translates paragraphs preceding source blocks`() {
        val input = """
            = Titre
            @cheroliv
            2025-01-01
            :jbake-title: Titre
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2025-01-01
            :revdate: 2025-01-01

            Le schéma ci-dessous récapitule l'ensemble du processus :

            [source,bash]
            ----
            echo "code"
            ----
        """.trimIndent()

        val result = adapter.translate(input, "fr", "en")

        assertTrue(result.contains("Le schéma ci-dessous récapitule l'ensemble du processus : [EN]"), "intro paragraph translated")
        assertTrue(result.contains("echo \"code\""), "source block preserved")
    }

    @Test
    fun `translate via pool does not translate source blocks`() {
        val input = """
            = Titre
            @cheroliv
            2025-01-01
            :jbake-title: Titre
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2025-01-01
            :revdate: 2025-01-01

            Un paragraphe.

            [source,bash]
            ----
            sudo apt install zsh
            ----
        """.trimIndent()

        val result = adapter.translate(input, "fr", "en")

        assertEquals(1, Regex("sudo apt install zsh").findAll(result).count())
        assertTrue(result.contains("Un paragraphe. [EN]"))
    }

    @Test
    fun `round trips inline admonition without block delimiters`() {
        val input = """
            = Titre
            @cheroliv
            2025-01-01
            :jbake-title: Titre
            :jbake-type: post
            :jbake-status: published
            :jbake-date: 2025-01-01
            :revdate: 2025-01-01

            [NOTE]
            Le fine-tune n'est pas un remplacement du RAG. Il reste pertinent pour les donnees qui changent souvent.

            == Heading after
        """.trimIndent()

        val result = adapter.translate(input, "fr", "en")

        assertTrue(result.contains("[NOTE]"), "admonition kind preserved")
        assertTrue(result.contains("Il reste pertinent pour les donnees qui changent souvent. [EN]"), "inline admonition text translated")
        assertTrue(result.contains("== Heading after [EN]"), "following heading translated")
    }

    @Test
    fun `parseNumberedLines tolerates list markers and noise`() {
        val response = """
            Here are the translations:

            - 1: Premier [EN]
            - 2: Second [EN]
            3. Troisième [EN]

            Done!
        """.trimIndent()

        val parsed = TreeTranslationAdapter.parseNumberedLines(response)

        assertEquals(3, parsed.size)
        assertEquals("Premier [EN]", parsed[1])
        assertEquals("Second [EN]", parsed[2])
        assertEquals("Troisième [EN]", parsed[3])
    }

    @Test
    fun `parseNumberedLines skips non-numbered noise`() {
        val response = """
            I cannot translate this.
            - 1: Premier [EN]
            Nope.
        """.trimIndent()

        val parsed = TreeTranslationAdapter.parseNumberedLines(response)

        assertEquals(1, parsed.size)
        assertEquals("Premier [EN]", parsed[1])
    }
}
