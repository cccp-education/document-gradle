package document.translation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DOC-TRANSLATE — the translatability classifier must never mistake *prose* for a
 * technical fragment.
 *
 * The 0119 benchmark article (cheroliv.com) carries ordinary French paragraphs
 * mentioning `120+ Go`, `128 Go unifié`, `128 Go HBM`: the unanchored `\d+\s*Go`
 * pattern matched anywhere in the fragment, so the whole paragraph (and the list
 * item next to it) was classified non-translatable and shipped in French in all
 * 21 languages. A technical *fragment* is short; a sentence is prose.
 */
class TextTranslatableClassifierTest {

    @Test
    fun `a short disk-size fragment stays technical`() {
        assertFalse(TextTranslatableClassifier.isTranslatable("128 Go"))
        assertFalse(TextTranslatableClassifier.isTranslatable("~64 Go sur 128 Go"))
        assertFalse(TextTranslatableClassifier.isTranslatable("64 Mo"))
    }

    @Test
    fun `a short sentence mentioning a disk size is prose`() {
        assertTrue(TextTranslatableClassifier.isTranslatable("La mémoire est de 64 Mo"))
    }

    @Test
    fun `a prose paragraph mentioning a disk size is translatable`() {
        val paragraph =
            "Huawei reste focalisé sur le marché des serveurs et des centres de données. " +
                "Pour un boîtier compact « prêt à brancher » avec 120+ Go de VRAM, le DGX Spark " +
                "(128 Go unifié) et le Mac Studio M3 Ultra (192 Go unifié) sont les seules options en 2026."

        assertTrue(TextTranslatableClassifier.isTranslatable(paragraph))
    }

    @Test
    fun `a list item carrying technical tokens and French prose is translatable`() {
        val item = " : 128 Go HBM, 800 TFLOPS FP16 — mais c'est une puce serveur, pas un desktop"

        assertTrue(TextTranslatableClassifier.isTranslatable(item))
    }

    @Test
    fun `a package list is never sent to the model`() {
        assertFalse(TextTranslatableClassifier.isTranslatable("kotlin, asciidoctor, plantuml"))
    }

    @Test
    fun `a sentence with a technical token is prose, a bare fragment is not`() {
        assertTrue(TextTranslatableClassifier.isTranslatable("Le processeur embarque 128 Go de mémoire unifiée"))
        assertFalse(TextTranslatableClassifier.isTranslatable("128 Go HBM"))
    }

    @Test
    fun `a pure asciidoc macro is never translatable`() {
        assertFalse(TextTranslatableClassifier.isTranslatable("toc::[]"))
        assertFalse(TextTranslatableClassifier.isTranslatable("image::schema.png[]"))
    }

    @Test
    fun `a pure inline code span is never translatable`() {
        assertFalse(TextTranslatableClassifier.isTranslatable("`gradle build`"))
    }

    @Test
    fun `an anchor and an attribute reference alone are never translatable`() {
        assertFalse(TextTranslatableClassifier.isTranslatable("[[page-10]]"))
        assertFalse(TextTranslatableClassifier.isTranslatable("[cols=\"1,1\"]"))
    }

    @Test
    fun `prose carrying a macro stays translatable`() {
        assertTrue(TextTranslatableClassifier.isTranslatable("Voir toc::[] pour la table des matières"))
    }

    @Test
    fun `plain prose stays translatable`() {
        assertTrue(TextTranslatableClassifier.isTranslatable("Bonjour le monde"))
    }
}
