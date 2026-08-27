package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookTreeBuilderTest {

    private fun section(ref: String, title: String = "Section $ref") =
        BookSection(ref = ref, title = title, page = 1, pdfFile = "$ref.pdf")

    // --- depth derivation (level = number of segments minus one) ---

    @Test
    fun `level of deep ref 1_0_2_6_4 is 4`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.2.6.4")))
        assertEquals(4, tree.node("1.0.2.6.4")!!.level)
    }

    @Test
    fun `level of ref 1_0_1 is 2`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.1")))
        assertEquals(2, tree.node("1.0.1")!!.level)
    }

    @Test
    fun `root node has level 0`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.1")))
        assertEquals(0, tree.root.level)
        assertEquals("", tree.root.ref)
    }

    @Test
    fun `level increases by exactly one per ref segment`() {
        val tree = BookTreeBuilder.fromSections(
            listOf(section("1"), section("1.0"), section("1.0.2"), section("1.0.2.6.4"))
        )
        assertEquals(0, tree.node("1")!!.level)
        assertEquals(1, tree.node("1.0")!!.level)
        assertEquals(2, tree.node("1.0.2")!!.level)
        assertEquals(4, tree.node("1.0.2.6.4")!!.level)
    }

    // --- parent link ---

    @Test
    fun `node is linked to its parent in the tree`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.2.6.4")))
        val child = tree.node("1.0.2.6.4")!!
        val parent = tree.node("1.0.2.6")!!
        assertTrue(parent.children.any { it.ref == "1.0.2.6.4" }, "parent must contain child")
        assertEquals(parent.ref, "1.0.2.6")
    }

    @Test
    fun `parent chain reaches the synthetic root`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.2.6.4")))
        assertEquals("1.0.2.6", tree.node("1.0.2.6.4")!!.let { findParentRef(tree, it.ref) })
        assertEquals("1.0.2", findParentRef(tree, "1.0.2.6"))
        assertEquals("1.0", findParentRef(tree, "1.0.2"))
        assertEquals("1", findParentRef(tree, "1.0"))
        assertEquals("", findParentRef(tree, "1"))
    }

    // --- ref uniqueness ---

    @Test
    fun `duplicate ref sections collapse into a single node`() {
        val tree = BookTreeBuilder.fromSections(
            listOf(section("1.0.1", "Premier"), section("1.0.1", "Deuxieme"))
        )
        assertEquals(1, tree.nodesByRef.values.count { it.ref == "1.0.1" })
        assertEquals("Premier", tree.node("1.0.1")!!.source!!.title)
    }

    // --- orphan ref (parent missing in TOC) ---

    @Test
    fun `orphan ref builds synthetic ancestor chain`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("3.2.1")))
        assertNotNull(tree.node("3"), "synthetic ancestor 3 must exist")
        assertNotNull(tree.node("3.2"), "synthetic ancestor 3.2 must exist")
        assertTrue(tree.node("3.2")!!.children.any { it.ref == "3.2.1" })
        assertTrue(tree.node("3")!!.children.any { it.ref == "3.2" })
        assertNull(tree.node("3.2")!!.source, "synthetic node has no source page")
    }

    // --- ordering ---

    @Test
    fun `children are ordered by ref ascending`() {
        val tree = BookTreeBuilder.fromSections(
            listOf(section("1.0.2"), section("1.0.1"), section("1.0.3"))
        )
        assertEquals(listOf("1.0.1", "1.0.2", "1.0.3"), tree.node("1.0")!!.children.map { it.ref })
    }

    // --- multi-level ---

    @Test
    fun `multi-level tree preserves full depth and leaves`() {
        val sections = listOf(section("1.0.1"), section("1.0.2.6"), section("1.0.2.6.4"))
        val tree = BookTreeBuilder.fromSections(sections)
        assertEquals(sections, tree.leaves)
        assertEquals(2, tree.node("1.0.1")!!.level)
        assertEquals(4, tree.node("1.0.2.6.4")!!.level)
        assertTrue(tree.node("1.0")!!.children.any { it.ref == "1.0.1" })
        assertTrue(tree.node("1.0.2.6")!!.children.any { it.ref == "1.0.2.6.4" })
    }

    // --- empty input ---

    @Test
    fun `empty sections produce an empty tree`() {
        val tree = BookTreeBuilder.fromSections(emptyList())
        assertEquals("", tree.root.ref)
        assertTrue(tree.root.children.isEmpty())
        assertTrue(tree.nodesByRef.isEmpty())
        assertTrue(tree.leaves.isEmpty())
    }

    // --- index completeness ---

    @Test
    fun `nodesByRef contains every real and synthetic ref`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.2.6.4"), section("2.1")))
        val expectedRefs = setOf("", "1", "1.0", "1.0.2", "1.0.2.6", "1.0.2.6.4", "2", "2.1")
        assertEquals(expectedRefs, tree.nodesByRef.keys)
    }

    @Test
    fun `root children are the top-level refs`() {
        val tree = BookTreeBuilder.fromSections(listOf(section("1.0.1"), section("2.0.1")))
        assertEquals(listOf("1", "2"), tree.root.children.map { it.ref })
    }

    @Test
    fun `tree is acyclic and deterministic across two builds`() {
        val sections = listOf(section("1.0.2.6.4"), section("2.1"), section("1.0.1"))
        val a = BookTreeBuilder.fromSections(sections)
        val b = BookTreeBuilder.fromSections(sections)
        assertEquals(a.nodesByRef.keys, b.nodesByRef.keys)
        assertEquals(a.root.children.map { it.ref }, b.root.children.map { it.ref })
        assertFalse(a.root.children.isEmpty())
    }

    private fun findParentRef(tree: BookTree, ref: String): String? {
        for ((parentRef, node) in tree.nodesByRef) {
            if (node.children.any { it.ref == ref }) return parentRef
        }
        return null
    }
}
