package document.translation

import contracts.i18n.PreserveCatalog
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MEM-I18N-PRESERVE / PRESERVE-2 — the shared vectors of the "never translate"
 * contract, replayed by document.
 *
 * The vectors file is **byte-identical** to plantuml's
 * (`plantuml-plugin/src/test/resources/plantuml/boundary/preserve-vectors.tsv`):
 * the two consumers implement the same N0 rule on two formats, and a divergence
 * between them is the drift this guard exists for. The drift test is skipped when
 * the sibling checkout is absent (isolated CI), never silently passed.
 */
class PreserveVectorsTest {

    private val vectors: List<Vector> = loadVectors()

    @Test
    fun `the shared vectors pin the N0 rule`() {
        assertEquals(
            listOf("macro", "macro-with-target", "inline-code", "anchor", "attribute-reference", "url",
                "block-attribute", "fence", "prose-with-macro", "prose", "disk-size", "technology"),
            vectors.map { it.id },
        )
    }

    @Test
    fun `document replays the shared vectors`() {
        vectors.forEach { vector ->
            assertEquals(
                vector.fullyPreserved,
                PreserveCatalog.isFullyPreserved(vector.text),
                vector.id,
            )
        }
    }

    @Test
    fun `a pure marker fragment is never translatable, prose citing one is`() {
        vectors.filter { it.fullyPreserved }.forEach { vector ->
            assertEquals(false, TextTranslatableClassifier.isTranslatable(vector.text), vector.id)
        }
        assertEquals(true, TextTranslatableClassifier.isTranslatable("Voir toc::[] pour la table des matières"))
    }

    @Test
    fun `the vectors file is byte-identical to the plantuml one`() {
        val sibling = siblingVectors()
        assumeTrue(sibling != null) { "plantuml checkout absent (isolated CI) — drift guard skipped" }

        assertEquals(sibling!!.readText(), vectorsFile().readText(), "preserve-vectors.tsv drifted from plantuml")
    }

    private fun siblingVectors(): File? =
        File("../../plantuml-gradle/plantuml-plugin/src/test/resources/plantuml/boundary/preserve-vectors.tsv")
            .takeIf { it.isFile }

    private fun loadVectors(): List<Vector> =
        vectorsFile().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split('\t')
                Vector(parts[0], parts[1].toBoolean(), parts.drop(2).joinToString("\t"))
            }

    private fun vectorsFile(): File {
        val url = javaClass.classLoader.getResource("document/translation/preserve-vectors.tsv")
            ?: error("preserve-vectors.tsv missing")
        return File(url.toURI())
    }

    private data class Vector(
        val id: String,
        val fullyPreserved: Boolean,
        val text: String,
    )
}
