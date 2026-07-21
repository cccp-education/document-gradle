package document.translation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslateDocumentTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `translateDocument parses and translates a simple AsciiDoc article`() {
        val source = tempDir.resolve("source.adoc")
        source.writeText("""title=Bonjour le monde
date=2026-07-20
type=page
status=published
~~~~~~

== Introduction

Ceci est un paragraphe en francais.

== Conclusion

Fin du document.
""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        assertTrue(output.exists())
        val content = output.readText()
        assertTrue(content.contains("Bonjour le monde [EN]"))
        assertTrue(content.contains("Introduction [EN]"))
        assertTrue(content.contains("Ceci est un paragraphe en francais. [EN]"))
        assertTrue(content.contains("Conclusion [EN]"))
        assertTrue(content.contains("Fin du document. [EN]"))
    }

    @Test
    fun `translateDocument preserves non-translatable blocks like source code`() {
        val source = tempDir.resolve("source.adoc")
        source.writeText("""title=Code Example
date=2026-07-20
type=page
status=published
~~~~~~

== Code Sample

[source,java]
----
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
----

Some text after code.
""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        val content = output.readText()
        assertTrue(content.contains("public class Hello"))
        assertTrue(content.contains("System.out.println"))
        assertTrue(content.contains("Some text after code. [EN]"))
    }

    @Test
    fun `translateDocument preserves frontmatter structure`() {
        val source = tempDir.resolve("source.adoc")
        source.writeText("""title=Mon Article
date=2026-07-20
type=blog
status=published
author=cheroliv
jbake-tags=java,kotlin
~~~~~~

== Contenu

Texte a traduire.
""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        val content = output.readText()
        assertTrue(content.contains("title=Mon Article [EN]"))
        assertTrue(content.contains("date=2026-07-20"))
        assertTrue(content.contains("type=blog"))
        assertTrue(content.contains("author=cheroliv"))
        assertTrue(content.contains("jbake-tags=java,kotlin"))
    }

    @Test
    fun `translateDocument handles empty document gracefully`() {
        val source = tempDir.resolve("source.adoc")
        source.writeText("""title=Vide
date=2026-07-20
type=page
status=published
~~~~~~

""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        assertTrue(output.exists())
        val content = output.readText()
        assertTrue(content.contains("title=Vide [EN]"))
    }

    @Test
    fun `translateDocument handles lists and admonitions`() {
        val source = tempDir.resolve("source.adoc")
        source.writeText("""title=Listes
date=2026-07-20
type=page
status=published
~~~~~~

== Elements

. Premier element
. Deuxieme element

NOTE: Ceci est une note importante.

* Puce un
* Puce deux
""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = AsciiDocRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        val content = output.readText()
        assertTrue(content.contains("Premier element [EN]"))
        assertTrue(content.contains("Deuxieme element [EN]"))
        assertTrue(content.contains("Ceci est une note importante. [EN]"))
        assertTrue(content.contains("Puce un [EN]"))
        assertTrue(content.contains("Puce deux [EN]"))
    }

    @Test
    fun `translateDocument preserves cheroliv com JBake native article`() {
        val source = tempDir.resolve("0011_gradle_configuration_ide_jbake_content_post.adoc")
        source.writeText("""= Gradle: dossier resources visible dans l'IDE
@CherOliv
2019-08-16
:jbake-title: Gradle: dossier resources visible dans l'IDE
:jbake-tags: blog, methodologie, projet, cascade, classique, predictitive, management
:jbake-type: post
:jbake-status: published
:jbake-date: 2019-08-16
:summary: ajouter un dossier au classpath d'un build gradle

Dans un projet applicatif piloté par le gestionnaire de build Gradle +
il est plus agréable d'avoir la visibilité sur tous les dossiers +
participant au développement de l'application.

[source,groovy,numbered]
----
plugins {
    id "java"
    id "groovy"
}
----
""")
        val output = tempDir.resolve("output.adoc")

        val parser = AsciiDocParser()
        val renderer = JbakeNativeRenderer()
        val fake = FakeTranslationService(" [EN]")
        val service = ContentTranslationService(fake, parser, renderer)

        val original = source.readText()
        val article = parser.parse(original)
        val translated = service.translateArticle(article, "fr", "en")
        val rendered = renderer.render(translated)

        output.writeText(rendered)

        assertTrue(output.exists())
        val content = output.readText()
        val firstLine = content.lineSequence().firstOrNull()
        assertTrue(firstLine?.startsWith("= ") == true, "expected JBake native header (= ...) on first line, got: $firstLine")
        assertTrue(content.contains("@CherOliv"))
        assertTrue(content.contains("2019-08-16"))
        assertTrue(content.contains(":jbake-type: post"))
        assertTrue(content.contains(":jbake-status: published"))
        assertTrue(content.contains(":jbake-date: 2019-08-16"))
        assertTrue(content.contains(":summary:"))
        assertTrue(content.contains("plugins {"))
        assertTrue(content.contains("id \"java\""))
        assertTrue(content.contains("Dans un projet applicatif piloté par le gestionnaire de build Gradle"))
        assertTrue(content.contains("[EN]"), "expected fake translation marker in body")
        assertFalse(content.contains("title="), "must not emit broken YAML frontmatter")
        assertFalse(content.contains("~~~~~~"), "must not emit broken YAML separator")
    }
}
