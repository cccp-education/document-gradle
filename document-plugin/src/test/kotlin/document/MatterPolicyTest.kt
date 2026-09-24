package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [MatterPolicy] (DOC-BOOK-MATTER-1).
 *
 * [MatterPolicy] is the configurable / derivable counterpart of the legacy
 * [Matter.classify] convention: instead of a blind `0.x` / `9.x` hardcode, the
 * front and back root segments are an explicit, injectable mapping, and a book
 * only *requires* a matter when the policy actually declares a root for it.
 *
 * Ink Economy Law: classification is a pure function of the ref and the
 * policy — no I/O, fully deterministic and testable in isolation.
 */
class MatterPolicyTest {

    // --- default (legacy-compatible) policy ---

    @Test
    fun `default policy classifies a 0 root as FRONT`() {
        assertEquals(Matter.FRONT, MatterPolicy.DEFAULT.classify("0.1"))
        assertEquals(Matter.FRONT, MatterPolicy.DEFAULT.classify("0.5.2"))
    }

    @Test
    fun `default policy classifies a 9 root as BACK`() {
        assertEquals(Matter.BACK, MatterPolicy.DEFAULT.classify("9.1"))
        assertEquals(Matter.BACK, MatterPolicy.DEFAULT.classify("9.0.3"))
    }

    @Test
    fun `default policy classifies any other root as BODY`() {
        assertEquals(Matter.BODY, MatterPolicy.DEFAULT.classify("1.0.1"))
        assertEquals(Matter.BODY, MatterPolicy.DEFAULT.classify("2.3.4.5"))
    }

    @Test
    fun `a bare top-level root is classified by its own segment`() {
        assertEquals(Matter.FRONT, MatterPolicy.DEFAULT.classify("0"))
        assertEquals(Matter.BACK, MatterPolicy.DEFAULT.classify("9"))
        assertEquals(Matter.BODY, MatterPolicy.DEFAULT.classify("1"))
    }

    @Test
    fun `an empty ref is BODY`() {
        assertEquals(Matter.BODY, MatterPolicy.DEFAULT.classify(""))
    }

    // --- explicit mapping ---

    @Test
    fun `an explicit mapping classifies its declared roots`() {
        val policy = MatterPolicy(frontRoots = setOf("A"), backRoots = setOf("Z"))
        assertEquals(Matter.FRONT, policy.classify("A.1"))
        assertEquals(Matter.BACK, policy.classify("Z.2"))
        assertEquals(Matter.BODY, policy.classify("1.0.1"))
    }

    @Test
    fun `several roots may map to the same matter`() {
        val policy = MatterPolicy(frontRoots = setOf("0", "i"), backRoots = setOf("9", "z"))
        assertEquals(Matter.FRONT, policy.classify("i.1"))
        assertEquals(Matter.BACK, policy.classify("z.1"))
    }

    // --- requirement derivation ---

    @Test
    fun `the default policy requires both front and back matter`() {
        assertTrue(MatterPolicy.DEFAULT.requiresFront, "the default policy must require FRONT")
        assertTrue(MatterPolicy.DEFAULT.requiresBack, "the default policy must require BACK")
    }

    @Test
    fun `the NONE policy requires nothing`() {
        assertFalse(MatterPolicy.NONE.requiresFront, "the NONE policy must not require FRONT")
        assertFalse(MatterPolicy.NONE.requiresBack, "the NONE policy must not require BACK")
    }

    @Test
    fun `a policy without a front root does not require FRONT`() {
        val policy = MatterPolicy(frontRoots = emptySet(), backRoots = setOf("9"))
        assertFalse(policy.requiresFront)
        assertTrue(policy.requiresBack)
    }

    @Test
    fun `the NONE policy classifies every ref as BODY`() {
        assertEquals(Matter.BODY, MatterPolicy.NONE.classify("0.1"))
        assertEquals(Matter.BODY, MatterPolicy.NONE.classify("9.1"))
        assertEquals(Matter.BODY, MatterPolicy.NONE.classify("1.0.1"))
    }

    // --- invariants and factory ---

    @Test
    fun `overlapping front and back roots are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MatterPolicy(frontRoots = setOf("0"), backRoots = setOf("0"))
        }
    }

    @Test
    fun `the of factory builds an explicit policy`() {
        val policy = MatterPolicy.of(frontRoots = setOf("i"), backRoots = setOf("z"))
        assertEquals(Matter.FRONT, policy.classify("i.1"))
        assertEquals(Matter.BACK, policy.classify("z.1"))
    }

    // --- derivation from a real table of contents ---

    private fun sections(vararg refs: String) = refs.mapIndexed { index, ref ->
        BookSection(ref = ref, title = "Section $ref", page = index + 1, pdfFile = "%03d.adoc".format(index + 1))
    }

    @Test
    fun `derive requires front and back when the TOC declares both roots`() {
        val policy = MatterPolicy.derive(sections("0.1", "1", "9.1"))
        assertTrue(policy.requiresFront)
        assertTrue(policy.requiresBack)
        assertEquals(Matter.FRONT, policy.classify("0.1"))
        assertEquals(Matter.BACK, policy.classify("9.1"))
    }

    @Test
    fun `derive requires nothing when the TOC declares no matter root`() {
        val policy = MatterPolicy.derive(sections("1.0.1", "2.1.1", "3.4.2", "4.3.5"))
        assertFalse(policy.requiresFront, "a body-only TOC must not require FRONT")
        assertFalse(policy.requiresBack, "a body-only TOC must not require BACK")
        assertEquals(Matter.BODY, policy.classify("1.0.1"))
    }

    @Test
    fun `derive only requires the matters the TOC actually declares`() {
        val policy = MatterPolicy.derive(sections("0.1", "1", "1.1"))
        assertTrue(policy.requiresFront)
        assertFalse(policy.requiresBack, "a TOC without a 9 root must not require BACK")
    }

    @Test
    fun `derive of an empty TOC requires nothing`() {
        val policy = MatterPolicy.derive(emptyList())
        assertFalse(policy.requiresFront)
        assertFalse(policy.requiresBack)
    }

    // --- mode resolution (Gradle-facing) ---

    @Test
    fun `forMode DERIVED adopts the matter roots the TOC declares`() {
        val policy = MatterPolicy.forMode(MatterPolicyMode.DERIVED, sections("1", "1.1", "2"))
        assertFalse(policy.requiresFront)
        assertFalse(policy.requiresBack)
    }

    @Test
    fun `forMode LEGACY keeps the 0_9 convention whatever the TOC is`() {
        val policy = MatterPolicy.forMode(MatterPolicyMode.LEGACY, sections("1", "1.1"))
        assertTrue(policy.requiresFront)
        assertTrue(policy.requiresBack)
        assertEquals(MatterPolicy.DEFAULT, policy)
    }

    @Test
    fun `forMode NONE requires nothing`() {
        val policy = MatterPolicy.forMode(MatterPolicyMode.NONE, sections("0.1", "9.1"))
        assertEquals(MatterPolicy.NONE, policy)
    }
}
