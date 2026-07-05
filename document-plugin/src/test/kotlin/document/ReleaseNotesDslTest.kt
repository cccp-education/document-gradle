package document

import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReleaseNotesDslTest {

    private val objects: ObjectFactory = ProjectBuilder.builder().build().objects

    private fun dsl() = ReleaseNotesDsl(
        fromTag = objects.property(String::class.java),
        toTag = objects.property(String::class.java),
        version = objects.property(String::class.java),
        includeDownloads = objects.property(Boolean::class.java),
        rendererType = objects.property(String::class.java),
        categories = objects.mapProperty(String::class.java, String::class.java),
    )

    @Test
    fun `default rendererType is null (falls back to asciidoc)`() {
        val dsl = dsl()
        assertNull(dsl.rendererType.orNull)
    }

    @Test
    fun `rendererType can be set to markdown`() {
        val dsl = dsl()
        dsl.rendererType.set("markdown")
        assertEquals("markdown", dsl.rendererType.get())
    }

    @Test
    fun `rendererType can be set to json`() {
        val dsl = dsl()
        dsl.rendererType.set("json")
        assertEquals("json", dsl.rendererType.get())
    }

    @Test
    fun `categories default to empty (config fallback used)`() {
        val dsl = dsl()
        assertEquals(0, dsl.categories.get().size)
    }

    @Test
    fun `categories can be customised with a custom map`() {
        val dsl = dsl()
        dsl.categories.set(
            mapOf(
                "feat" to "New features",
                "fix" to "Bug fixes",
                "custom" to "Custom category",
            ),
        )
        val cats = dsl.categories.get()
        assertEquals("New features", cats["feat"])
        assertEquals("Bug fixes", cats["fix"])
        assertEquals("Custom category", cats["custom"])
        assertEquals(3, cats.size)
    }

    @Test
    fun `fromTag toTag version includeDownloads remain configurable alongside new props`() {
        val dsl = dsl()
        dsl.fromTag.set("v1.0.0")
        dsl.toTag.set("HEAD")
        dsl.version.set("2.0.0")
        dsl.includeDownloads.set(false)
        dsl.rendererType.set("markdown")
        dsl.categories.set(mapOf("feat" to "Nouveautés"))
        assertEquals("v1.0.0", dsl.fromTag.get())
        assertEquals("HEAD", dsl.toTag.get())
        assertEquals("2.0.0", dsl.version.get())
        assertEquals(false, dsl.includeDownloads.get())
        assertEquals("markdown", dsl.rendererType.get())
        assertEquals("Nouveautés", dsl.categories.get()["feat"])
    }
}