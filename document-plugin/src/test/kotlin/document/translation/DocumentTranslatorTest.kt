package document.translation

import document.translation.plantuml.PlantUmlTranslationAdapter
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentTranslatorTest {

    private val fakeService = FakeTranslationService(" [EN]")
    private val translator = DocumentTranslator(fakeService)

    @Test
    fun `translate parses and translates a simple AsciiDoc article`() {
        val source = """title=Bonjour le monde
date=2026-07-20
type=page
status=published
~~~~~~

== Introduction

Ceci est un paragraphe en francais.

== Conclusion

Fin du document.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Bonjour le monde [EN]"))
        assertTrue(result.contains("Introduction [EN]"))
        assertTrue(result.contains("Ceci est un paragraphe en francais. [EN]"))
        assertTrue(result.contains("Conclusion [EN]"))
        assertTrue(result.contains("Fin du document. [EN]"))
    }

    @Test
    fun `translate preserves non-translatable blocks like source code`() {
        val source = """title=Code Example
date=2026-07-20
type=page
status=published
~~~~~~

== Code

[source,java]
----
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
----

Some text after code.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("public class Hello"))
        assertTrue(result.contains("System.out.println"))
        assertTrue(result.contains("Some text after code. [EN]"))
    }

    @Test
    fun `translate preserves frontmatter structure`() {
        val source = """title=Mon Article
date=2026-07-20
type=post
status=published
~~~~~~

== Content

Some paragraph text.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("title=Mon Article [EN]"))
        assertTrue(result.contains("date=2026-07-20"))
        assertTrue(result.contains("type=post"))
        assertTrue(result.contains("Content [EN]"))
        assertTrue(result.contains("Some paragraph text. [EN]"))
    }

    @Test
    fun `translate handles empty document gracefully`() {
        val source = """title=Empty
date=2026-07-20
type=page
status=published
~~~~~~

"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("title=Empty [EN]"))
    }

    @Test
    fun `translate handles lists and admonitions`() {
        val source = """title=Lists
date=2026-07-20
type=page
status=published
~~~~~~

== Items

. Premier element
. Deuxieme element

* Puce un
* Puce deux

NOTE: Ceci est une note importante.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Premier element [EN]"))
        assertTrue(result.contains("Deuxieme element [EN]"))
        assertTrue(result.contains("Puce un [EN]"))
        assertTrue(result.contains("Puce deux [EN]"))
        assertTrue(result.contains("Ceci est une note importante. [EN]"))
    }

    @Test
    fun `translate handles JBake native header format`() {
        val source = """= Mon Article JBake
@cheroliv
2026-07-20
:jbake-type: post
:jbake-tags: blog,tech
:jbake-status: published

== Introduction

Ceci est un article au format JBake native.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("= Mon Article JBake [EN]"))
        assertTrue(result.contains("@cheroliv"))
        assertTrue(result.contains("2026-07-20"))
        assertTrue(result.contains(":jbake-type: post"))
        assertTrue(result.contains(":jbake-tags: blog,tech"))
        assertTrue(result.contains(":jbake-status: published"))
        assertTrue(result.contains("Introduction [EN]"))
        assertTrue(result.contains("Ceci est un article au format JBake native. [EN]"))
    }

    @Test
    fun `translate handles tables`() {
        val source = """title=Table
date=2026-07-20
type=page
status=published
~~~~~~

== Data

[cols="2,2"]
|===
| Colonne A | Colonne B
| Valeur 1 | Valeur 2
|===
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Colonne A [EN]"))
        assertTrue(result.contains("Colonne B [EN]"))
        assertTrue(result.contains("Valeur 1 [EN]"))
        assertTrue(result.contains("Valeur 2 [EN]"))
    }

    @Test
    fun `translate handles inline code as non-translatable`() {
        val source = """title=Inline Code
date=2026-07-20
type=page
status=published
~~~~~~

== Usage

Use `System.out.println` to print text.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("`System.out.println`"))
        assertTrue(result.contains("to print text. [EN]"))
    }

    @Test
    fun `translate handles bold text`() {
        val source = """title=Bold
date=2026-07-20
type=page
status=published
~~~~~~

== Important

**Ceci est important** et ceci ne l'est pas.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("**Ceci est important [EN]**"))
        assertTrue(result.contains("et ceci ne l'est pas. [EN]"))
    }

    @Test
    fun `translate handles links`() {
        val source = """title=Links
date=2026-07-20
type=page
status=published
~~~~~~

== References

Voir https://example.com[le site officiel] pour plus d'infos.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("https://example.com[le site officiel [EN]]"))
        assertTrue(result.contains("pour plus d'infos. [EN]"))
    }

    @Test
    fun `translate handles plantuml blocks when adapter is wired`() {
        val source = """title=Diagram
date=2026-07-20
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
Alice -> Bob: "Bonjour"
@enduml
----
"""

        val plantUmlAdapter = document.translation.plantuml.PlantUmlTranslationAdapter(fakeService)
        val translatorWithPlantUml = DocumentTranslator(fakeService, plantUmlAdapter = plantUmlAdapter)

        val result = translatorWithPlantUml.translate(source, "fr", "en")

        assertTrue(result.contains("\"Bonjour [EN]\""))
    }

    @Test
    fun `translate preserves plantuml blocks when no adapter is wired`() {
        val source = """title=Diagram
date=2026-07-20
type=page
status=published
~~~~~~

== Architecture

[plantuml]
----
@startuml
Alice -> Bob: Bonjour
@enduml
----
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("Alice -> Bob: Bonjour"))
    }

    @Test
    fun `translate handles translation failure gracefully`() {
        val failingService = object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult =
                TranslationResult.Failure("LLM unavailable")
        }
        val failingTranslator = DocumentTranslator(failingService)

        val source = """title=Test
date=2026-07-20
type=page
status=published
~~~~~~

== Content

Ce texte ne sera pas traduit.
"""

        val result = failingTranslator.translate(source, "fr", "en")

        assertTrue(result.contains("title=Test"))
        assertTrue(result.contains("Ce texte ne sera pas traduit."))
    }

    @Test
    fun `translate handles horizontal rules`() {
        val source = """title=HR
date=2026-07-20
type=page
status=published
~~~~~~

== Section 1

Texte section un.

---

== Section 2

Texte section deux.
"""

        val result = translator.translate(source, "fr", "en")

        assertTrue(result.contains("---"))
        assertTrue(result.contains("Texte section un. [EN]"))
        assertTrue(result.contains("Texte section deux. [EN]"))
    }
}
