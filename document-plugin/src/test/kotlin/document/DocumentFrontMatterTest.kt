package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentFrontMatterTest {

    @Test
    fun `DocumentFrontMatter default has sensible defaults`() {
        val fm = DocumentFrontMatter()
        assertEquals("Untitled Document", fm.title)
        assertEquals("Unknown Author", fm.author)
        assertEquals("fr", fm.language)
        assertTrue(fm.isDefault())
    }

    @Test
    fun `DocumentFrontMatter stores title author and language`() {
        val fm = DocumentFrontMatter(title = "Mon Livre", author = "Moi", language = "en")
        assertEquals("Mon Livre", fm.title)
        assertEquals("Moi", fm.author)
        assertEquals("en", fm.language)
    }

    @Test
    fun `DocumentFrontMatter isDefault is false when title overridden`() {
        val fm = DocumentFrontMatter(title = "Custom")
        assertTrue(!fm.isDefault())
    }

    @Test
    fun `DocumentFrontMatter toAsciiDocAttributes maps title author and lang`() {
        val fm = DocumentFrontMatter(title = "Livre", author = "Auteur", language = "fr")
        val attrs = fm.toAsciiDocAttributes()
        assertEquals("Livre", attrs["docname"])
        assertEquals("Auteur", attrs["author"])
        assertEquals("fr", attrs["lang"])
    }

    @Test
    fun `DocumentFrontMatter toAsciiDocAttributes skips null author`() {
        val fm = DocumentFrontMatter(title = "Livre", author = null, language = "fr")
        val attrs = fm.toAsciiDocAttributes()
        assertEquals("Livre", attrs["docname"])
        assertTrue(!attrs.containsKey("author"))
        assertEquals("fr", attrs["lang"])
    }
}