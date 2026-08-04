package document.translation.delta

import document.translation.PivotBlock
import document.translation.PivotInline
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockDeltaTest {

    private fun heading(text: String) = PivotBlock.Heading(1, text, translatable = true)
    private fun paragraph(vararg inlines: PivotInline) =
        PivotBlock.Paragraph(inlines.toList())
    private fun text(value: String) = PivotInline.Text(value, translatable = true)
    private fun plantumlBlock() = PivotBlock.Source("plantuml", "@startuml\nA --> B\n@enduml")
    private fun table(headerCell: String, rowCell: String) = PivotBlock.Table(
        cols = null,
        header = listOf(listOf(text(headerCell))),
        rows = listOf(listOf(listOf(text(rowCell))))
    )

    private fun translated(hash: String) = BlockChecksumEntry(hash, BlockTranslationStatus.TRANSLATED)
    private fun pending(hash: String) = BlockChecksumEntry(hash, BlockTranslationStatus.PENDING)

    @Test
    fun `empty previous and current produces empty delta`() {
        val delta = BlockDelta.compute(previous = emptyMap(), current = emptyMap())
        assertTrue(delta.modifiedBlocks.isEmpty())
        assertTrue(delta.preservedBlocks.isEmpty())
    }

    @Test
    fun `fresh blocks with no previous are all modified`() {
        val current = mapOf("0" to "hash-h1", "1" to "hash-p1")
        val delta = BlockDelta.compute(previous = emptyMap(), current = current)
        assertEquals(setOf("0", "1"), delta.modifiedBlocks.toSet())
        assertTrue(delta.preservedBlocks.isEmpty())
    }

    @Test
    fun `unchanged translated blocks are preserved`() {
        val previous = mapOf("0" to translated("hash-h1"), "1" to translated("hash-p1"))
        val current = mapOf("0" to "hash-h1", "1" to "hash-p1")
        val delta = BlockDelta.compute(previous, current)
        assertTrue(delta.modifiedBlocks.isEmpty())
        assertEquals(setOf("0", "1"), delta.preservedBlocks.toSet())
    }

    @Test
    fun `single modified block retranslates only that block`() {
        val previous = mapOf("0" to translated("hash-h1"), "1" to translated("hash-p1"))
        val current = mapOf("0" to "hash-h1", "1" to "hash-p1-modified")
        val delta = BlockDelta.compute(previous, current)
        assertEquals(setOf("1"), delta.modifiedBlocks.toSet())
        assertEquals(setOf("0"), delta.preservedBlocks.toSet())
    }

    @Test
    fun `diagram block preserved when surrounding text changes`() {
        val previous = mapOf(
            "0" to translated("hash-h1"),
            "1" to translated("hash-diagram"),
            "2" to translated("hash-p1")
        )
        val current = mapOf(
            "0" to "hash-h1-modified",
            "1" to "hash-diagram",
            "2" to "hash-p1-modified"
        )
        val delta = BlockDelta.compute(previous, current)
        assertEquals(setOf("0", "2"), delta.modifiedBlocks.toSet())
        assertEquals(setOf("1"), delta.preservedBlocks.toSet())
    }

    @Test
    fun `new block added is marked modified`() {
        val previous = mapOf("0" to translated("hash-h1"))
        val current = mapOf("0" to "hash-h1", "1" to "hash-p1-new")
        val delta = BlockDelta.compute(previous, current)
        assertEquals(setOf("1"), delta.modifiedBlocks.toSet())
        assertEquals(setOf("0"), delta.preservedBlocks.toSet())
    }

    @Test
    fun `removed block disappears from delta`() {
        val previous = mapOf("0" to translated("hash-h1"), "1" to translated("hash-p1"))
        val current = mapOf("0" to "hash-h1")
        val delta = BlockDelta.compute(previous, current)
        assertTrue(delta.modifiedBlocks.isEmpty())
        assertEquals(setOf("0"), delta.preservedBlocks.toSet())
    }

    @Test
    fun `BlockChecksum computes stable sha256 for heading`() {
        val block = heading("Introduction")
        val hash = BlockChecksum.sha256(block)
        assertEquals(64, hash.length)
        assertEquals(hash, BlockChecksum.sha256(heading("Introduction")))
    }

    @Test
    fun `BlockChecksum differs for different heading text`() {
        val a = BlockChecksum.sha256(heading("Introduction"))
        val b = BlockChecksum.sha256(heading("Conclusion"))
        assertTrue(a != b)
    }

    @Test
    fun `BlockChecksum computes sha256 for paragraph`() {
        val block = paragraph(text("Hello world"))
        val hash = BlockChecksum.sha256(block)
        assertEquals(64, hash.length)
        assertEquals(hash, BlockChecksum.sha256(paragraph(text("Hello world"))))
    }

    @Test
    fun `BlockChecksum for non-translatable source block is deterministic`() {
        val block = plantumlBlock()
        val hash = BlockChecksum.sha256(block)
        assertEquals(64, hash.length)
        assertEquals(hash, BlockChecksum.sha256(plantumlBlock()))
    }

    @Test
    fun `BlockChecksum differs when plantuml content changes`() {
        val a = BlockChecksum.sha256(plantumlBlock())
        val b = BlockChecksum.sha256(PivotBlock.Source("plantuml", "@startuml\nA --> C\n@enduml"))
        assertTrue(a != b)
    }

    @Test
    fun `BlockChecksum computes sha256 for table cell`() {
        val block = table("Name", "Alice")
        val hash = BlockChecksum.sha256(block)
        assertEquals(64, hash.length)
        assertEquals(hash, BlockChecksum.sha256(table("Name", "Alice")))
    }

    @Test
    fun `BlockChecksum differs for table with different cell content`() {
        val a = BlockChecksum.sha256(table("Name", "Alice"))
        val b = BlockChecksum.sha256(table("Name", "Bob"))
        assertTrue(a != b)
    }

    @Test
    fun `computeForBlocks produces index-keyed checksums map`() {
        val blocks = listOf(
            heading("Title"),
            paragraph(text("Body")),
            plantumlBlock()
        )
        val checksums = BlockChecksum.computeForBlocks(blocks)
        assertEquals(3, checksums.size)
        assertEquals(BlockChecksum.sha256(heading("Title")), checksums["0"])
        assertEquals(BlockChecksum.sha256(paragraph(text("Body"))), checksums["1"])
        assertEquals(BlockChecksum.sha256(plantumlBlock()), checksums["2"])
    }

    @Test
    fun `computeForBlocks skips hr and non-translatable block macro`() {
        val blocks = listOf<PivotBlock>(
            heading("Title"),
            PivotBlock.Hr,
            PivotBlock.BlockMacro("image", "diagram.png")
        )
        val checksums = BlockChecksum.computeForBlocks(blocks)
        assertEquals(1, checksums.size)
        assertTrue(checksums.containsKey("0"))
    }

    @Test
    fun `delta isEmpty when no modified blocks`() {
        val previous = mapOf("0" to translated("hash-h1"))
        val current = mapOf("0" to "hash-h1")
        val delta = BlockDelta.compute(previous, current)
        assertTrue(delta.isEmpty())
    }

    @Test
    fun `delta isNotEmpty when modified blocks exist`() {
        val previous = mapOf("0" to translated("hash-h1"))
        val current = mapOf("0" to "hash-h1-new")
        val delta = BlockDelta.compute(previous, current)
        assertTrue(delta.isNotEmpty())
    }

    @Test
    fun `pending status block is retranslated even with same hash`() {
        val previous = mapOf("0" to pending("hash-h1"))
        val current = mapOf("0" to "hash-h1")
        val delta = BlockDelta.compute(previous, current)
        assertEquals(setOf("0"), delta.modifiedBlocks.toSet())
        assertTrue(delta.preservedBlocks.isEmpty())
    }

    @Test
    fun `BlockChecksumEntry parse roundtrip`() {
        val entry = BlockChecksumEntry("abc123", BlockTranslationStatus.TRANSLATED)
        val serialized = entry.serialize()
        assertEquals("abc123:TRANSLATED", serialized)
        val parsed = BlockChecksumEntry.parse(serialized)
        assertEquals(entry, parsed)
    }

    @Test
    fun `BlockChecksumEntry parse legacy format defaults to PENDING`() {
        val parsed = BlockChecksumEntry.parse("abc123")
        assertEquals("abc123", parsed.hash)
        assertEquals(BlockTranslationStatus.PENDING, parsed.status)
    }
}