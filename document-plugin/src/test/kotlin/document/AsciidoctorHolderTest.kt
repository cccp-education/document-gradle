package document

import org.asciidoctor.Asciidoctor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * DOC-CR3-1 — verifie que l'instance AsciidoctorJ est creee une seule fois
 * et reutilisee sur conversions multiples (au lieu de Factory.create() par appel,
 * qui redemarre JRuby a chaque format du bookPipeline).
 */
class AsciidoctorHolderTest {

    private var createCount = 0
    private val countingProvider: () -> Asciidoctor = {
        createCount++
        Asciidoctor.Factory.create()
    }

    @AfterEach
    fun reset() {
        AsciidoctorHolder.resetProvider()
    }

    @Test
    fun `reutilise la meme instance AsciidoctorJ sur conversions multiples`() {
        AsciidoctorHolder.setProviderForTest(countingProvider)

        val dir = Files.createTempDirectory("asciidoc-holder").toFile()
        val source = DocumentSource(
            File(dir, "src.adoc").apply {
                writeText("= Titre\n\n== Section\n\nTexte.")
            }
        )

        DocumentConverter.convertToHtml(source)
        DocumentConverter.convertToHtml(source)

        assertEquals(
            1,
            createCount,
            "l'instance AsciidoctorJ doit etre creee une seule fois et reutilisee entre conversions"
        )
    }
}
