package document.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.File
import java.nio.file.Files

class TemplateEngineTest {

    private val engine = TemplateEngine()

    @Test
    fun `replaces single variable`() {
        val result = engine.apply("Hello {{name}}!", mapOf("name" to "World"))
        assertEquals("Hello World!", result)
    }

    @Test
    fun `replaces multiple variables`() {
        val template = "= {{title}}\n:author: {{author}}\n\n{{content}}"
        val vars = mapOf("title" to "My Book", "author" to "Jane Doe", "content" to "Chapter one.")
        val result = engine.apply(template, vars)
        assertEquals("= My Book\n:author: Jane Doe\n\nChapter one.", result)
    }

    @Test
    fun `leaves non-variable text unchanged`() {
        val result = engine.apply("= Static Title\n\nNo variables here.", emptyMap())
        assertEquals("= Static Title\n\nNo variables here.", result)
    }

    @Test
    fun `replaces same variable multiple times`() {
        val result = engine.apply("{{x}} + {{x}} = 2{{x}}", mapOf("x" to "a"))
        assertEquals("a + a = 2a", result)
    }

    @Test
    fun `throws when variable missing and failOnMissing is true`() {
        val ex = assertThrows(MissingVariableException::class.java) {
            engine.apply("Hello {{name}}!", emptyMap(), failOnMissing = true)
        }
        assertTrue(ex.missingVariables.contains("name"))
    }

    @Test
    fun `keeps placeholder when variable missing and failOnMissing is false`() {
        val result = engine.apply("Hello {{name}}!", emptyMap(), failOnMissing = false)
        assertEquals("Hello {{name}}!", result)
    }

    @Test
    fun `collects all missing variables`() {
        val ex = assertThrows(MissingVariableException::class.java) {
            engine.apply("{{a}} {{b}} {{c}}", mapOf("a" to "1"), failOnMissing = true)
        }
        assertEquals(setOf("b", "c"), ex.missingVariables)
    }

    @Test
    fun `ignores non-variable double braces`() {
        val result = engine.apply("{{not}} a {{var}}", mapOf("not" to "yes", "var" to "x"))
        assertEquals("yes a x", result)
    }

    @Test
    fun `applies template from file`() {
        val dir = Files.createTempDirectory("tmpl-test").toFile()
        val templateFile = File(dir, "template.adoc").apply {
            writeText("= {{title}}\n\n{{body}}")
        }
        val result = engine.applyFile(templateFile, mapOf("title" to "Doc", "body" to "Content."))
        assertEquals("= Doc\n\nContent.", result)
    }

    @Test
    fun `empty template returns empty string`() {
        val result = engine.apply("", mapOf("x" to "y"))
        assertEquals("", result)
    }

    @Test
    fun `empty variables map replaces nothing`() {
        val result = engine.apply("= {{title}}", emptyMap(), failOnMissing = false)
        assertEquals("= {{title}}", result)
    }

    @Test
    fun `variable names are case sensitive`() {
        val result = engine.apply("{{Title}} vs {{title}}", mapOf("Title" to "A", "title" to "B"))
        assertEquals("A vs B", result)
    }

    @Test
    fun `variable with underscore`() {
        val result = engine.apply("{{first_name}} {{last_name}}", mapOf("first_name" to "John", "last_name" to "Doe"))
        assertEquals("John Doe", result)
    }

    @Test
    fun `variable with digits`() {
        val result = engine.apply("v{{version}}", mapOf("version" to "2.0"))
        assertEquals("v2.0", result)
    }

    @Test
    fun `template with AsciiDoc structure`() {
        val template = """
            |= {{title}}
            |:author: {{author}}
            |:toc:
            |
            |== {{section1}}
            |
            |{{paragraph1}}
            |
            |== {{section2}}
            |
            |{{paragraph2}}
        """.trimMargin()
        val vars = mapOf(
            "title" to "User Guide",
            "author" to "Admin",
            "section1" to "Introduction",
            "paragraph1" to "Welcome to the guide.",
            "section2" to "Setup",
            "paragraph2" to "Follow these steps.",
        )
        val result = engine.apply(template, vars)
        assertTrue(result.contains("= User Guide"))
        assertTrue(result.contains(":author: Admin"))
        assertTrue(result.contains("== Introduction"))
        assertTrue(result.contains("Welcome to the guide."))
        assertTrue(result.contains("== Setup"))
        assertTrue(result.contains("Follow these steps."))
    }
}
