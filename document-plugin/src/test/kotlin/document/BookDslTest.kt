package document

import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookDslTest {

    private fun objects(): ObjectFactory = ProjectBuilder.builder().build().objects

    private fun dsl(): BookDsl = BookDsl(
        pagesDir = objects().directoryProperty(),
        photosDir = objects().directoryProperty(),
        title = objects().property(String::class.java),
        author = objects().property(String::class.java),
        tocFile = objects().fileProperty(),
        pdfsDir = objects().directoryProperty(),
        validationMode = objects().property(ValidationMode::class.java),
        matterPolicy = objects().property(MatterPolicyMode::class.java),
        matterBreaks = objects().property(Boolean::class.java),
        navigation = objects().property(Boolean::class.java),
        sourceLanguage = objects().property(String::class.java),
        targetLanguage = objects().property(String::class.java),
        translateToAll = objects().property(Boolean::class.java),
        targetLanguages = objects().listProperty(String::class.java),
        publishFormats = objects().listProperty(String::class.java),
        includeSource = objects().property(Boolean::class.java),
    )

    @Test
    fun `BookDsl holds pagesDir photosDir title and author properties`() {
        val dsl = dsl()
        assertNotNull(dsl.pagesDir)
        assertNotNull(dsl.photosDir)
        assertNotNull(dsl.title)
        assertNotNull(dsl.author)
    }

    @Test
    fun `BookDsl defaults are unset until convention applied`() {
        val dsl = dsl()
        assertFalse(dsl.title.isPresent)
        assertFalse(dsl.author.isPresent)
        assertFalse(dsl.pagesDir.isPresent)
        assertFalse(dsl.photosDir.isPresent)
    }

    @Test
    fun `BookDsl accepts convention defaults for title and author`() {
        val dsl = dsl()
        dsl.title.convention("Untitled Book")
        dsl.author.convention("Unknown Author")
        assertEquals("Untitled Book", dsl.title.get())
        assertEquals("Unknown Author", dsl.author.get())
    }

    @Test
    fun `BookDsl properties are settable and readable`() {
        val dsl = dsl()
        dsl.title.set("Mon Livre")
        dsl.author.set("Auteur")
        assertEquals("Mon Livre", dsl.title.get())
        assertEquals("Auteur", dsl.author.get())
    }

    @Test
    fun `BookDsl exposes the multi-language knobs`() {
        val dsl = dsl()
        dsl.translateToAll.set(true)
        dsl.targetLanguages.set(listOf("en", "de"))

        assertTrue(dsl.translateToAll.get())
        assertEquals(listOf("en", "de"), dsl.targetLanguages.get())
    }

    @Test
    fun `BookDsl exposes the publication formats knob`() {
        val dsl = dsl()
        dsl.publishFormats.set(listOf("html", "pdf"))

        assertEquals(listOf("html", "pdf"), dsl.publishFormats.get())
    }

    @Test
    fun `BookDsl exposes the includeSource knob`() {
        val dsl = dsl()
        dsl.includeSource.set(true)

        assertTrue(dsl.includeSource.get())
    }
}
