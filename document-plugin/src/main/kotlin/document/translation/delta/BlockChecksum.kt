package document.translation.delta

import document.translation.PivotBlock
import document.translation.PivotInline
import java.security.MessageDigest

enum class BlockTranslationStatus { TRANSLATED, PENDING }

data class BlockChecksumEntry(
    val hash: String,
    val status: BlockTranslationStatus
) {
    companion object {
        fun parse(raw: String): BlockChecksumEntry {
            val parts = raw.split(":", limit = 2)
            val hash = parts[0]
            val status = if (parts.size > 1) {
                try { BlockTranslationStatus.valueOf(parts[1]) } catch (_: Exception) { BlockTranslationStatus.PENDING }
            } else {
                BlockTranslationStatus.PENDING
            }
            return BlockChecksumEntry(hash, status)
        }
    }

    fun serialize(): String = "$hash:$status"
}

object BlockChecksum {

    fun sha256(block: PivotBlock): String {
        val payload = serialize(block)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeForBlocks(blocks: List<PivotBlock>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        blocks.forEachIndexed { index, block ->
            if (isChecksumRelevant(block)) {
                result[index.toString()] = sha256(block)
            }
        }
        return result
    }

    private fun isChecksumRelevant(block: PivotBlock): Boolean = when (block) {
        is PivotBlock.Hr -> false
        is PivotBlock.BlockMacro -> false
        else -> true
    }

    private fun serialize(block: PivotBlock): String = when (block) {
        is PivotBlock.Heading -> "H${block.level}:${block.text}"
        is PivotBlock.Paragraph -> "P:${serializeInlines(block.inline)}"
        is PivotBlock.ListBlock -> "L:${block.ordered}:${block.items.map { serializeInlines(it) }.joinToString("|")}"
        is PivotBlock.Table -> "T:${serializeRows(block.header)}:${block.rows.map { serializeRows(it) }.joinToString("|")}"
        is PivotBlock.Admonition -> "A:${block.kind}:${block.blocks.map { serialize(it) }.joinToString("|")}"
        is PivotBlock.Source -> "S:${block.language}:${block.content}"
        is PivotBlock.DescriptionList -> "D:${block.items.map { serializeInlines(it.term) + "=" + serializeInlines(it.definition) }.joinToString("|")}"
        is PivotBlock.BlockMacro -> "M:${block.name}:${block.target}"
        PivotBlock.Hr -> "HR"
    }

    private fun serializeInlines(inlines: List<PivotInline>): String =
        inlines.joinToString("|") { serializeInline(it) }

    private fun serializeRows(rows: List<List<PivotInline>>): String =
        rows.joinToString("|") { serializeInlines(it) }

    private fun serializeInline(inline: PivotInline): String = when (inline) {
        is PivotInline.Text -> "T:${inline.text}"
        is PivotInline.Bold -> "B:${inline.text}"
        is PivotInline.Code -> "C:${inline.text}"
        is PivotInline.Link -> "L:${inline.url}:${inline.label}"
        PivotInline.LineBreak -> "BR"
    }
}