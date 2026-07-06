package document

import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookDslTest {

    private fun objects(): ObjectFactory = ProjectBuilder.builder().build().objects

    @Test
    fun `BookDsl holds pagesDir photosDir title and author properties`() {
        val dsl = BookDsl(
            pagesDir = objects().directoryProperty(),
            photosDir = objects().directoryProperty(),
            title = objects().property(String::class.java),
            author = objects().property(String::class.java),
        )
        assertNotNull(dsl.pagesDir)
        assertNotNull(dsl.photosDir)
        assertNotNull(dsl.title)
        assertNotNull(dsl.author)
    }

    @Test
    fun `BookDsl defaults are unset until convention applied`() {
        val dsl = BookDsl(
            pagesDir = objects().directoryProperty(),
            photosDir = objects().directoryProperty(),
            title = objects().property(String::class.java),
            author = objects().property(String::class.java),
        )
        assertFalse(dsl.title.isPresent)
        assertFalse(dsl.author.isPresent)
        assertFalse(dsl.pagesDir.isPresent)
        assertFalse(dsl.photosDir.isPresent)
    }

    @Test
    fun `BookDsl accepts convention defaults for title and author`() {
        val dsl = BookDsl(
            pagesDir = objects().directoryProperty(),
            photosDir = objects().directoryProperty(),
            title = objects().property(String::class.java),
            author = objects().property(String::class.java),
        )
        dsl.title.convention("Untitled Book")
        dsl.author.convention("Unknown Author")
        assertEquals("Untitled Book", dsl.title.get())
        assertEquals("Unknown Author", dsl.author.get())
    }

    @Test
    fun `BookDsl properties are settable and readable`() {
        val dsl = BookDsl(
            pagesDir = objects().directoryProperty(),
            photosDir = objects().directoryProperty(),
            title = objects().property(String::class.java),
            author = objects().property(String::class.java),
        )
        dsl.title.set("Mon Livre")
        dsl.author.set("Auteur")
        assertEquals("Mon Livre", dsl.title.get())
        assertEquals("Auteur", dsl.author.get())
    }
}