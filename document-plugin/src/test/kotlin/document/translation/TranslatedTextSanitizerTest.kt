package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TranslatedTextSanitizerTest {
    @Test
    fun `a newline in a heading is collapsed to a single line`() {
        assertEquals("Introduction Injected", TranslatedTextSanitizer.sanitize("Introduction\nInjected"))
    }

    @Test
    fun `a paragraph containing a heading marker stays prose`() {
        assertEquals(
            "Texte == Heading",
            TranslatedTextSanitizer.sanitize("Texte\n== Heading"),
        )
    }

    @Test
    fun `a leading equals marker is escaped`() {
        assertEquals("\\= Heading", TranslatedTextSanitizer.sanitize("= Heading"))
    }

    @Test
    fun `a leading table delimiter is escaped`() {
        assertEquals("\\|===", TranslatedTextSanitizer.sanitize("|==="))
    }

    @Test
    fun `a leading plantuml attribute is escaped`() {
        assertEquals("\\[plantuml]", TranslatedTextSanitizer.sanitize("[plantuml]"))
    }

    @Test
    fun `leading source and literal delimiters are escaped`() {
        assertEquals("\\----", TranslatedTextSanitizer.sanitize("----"))
        assertEquals("\\....", TranslatedTextSanitizer.sanitize("...."))
        assertEquals("\\++++", TranslatedTextSanitizer.sanitize("++++"))
    }

    @Test
    fun `CR LF and CRLF become a single space`() {
        assertEquals("one two three", TranslatedTextSanitizer.sanitize("one\r\ntwo\rthree"))
    }

    @Test
    fun `normal prose is unchanged`() {
        assertEquals(
            "Le referentiel de competences du formateur.",
            TranslatedTextSanitizer.sanitize("Le referentiel de competences du formateur."),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Bonjour", TranslatedTextSanitizer.sanitize("  Bonjour  "))
    }
}
