package document.translation.delta

data class BlockDelta(
    val modifiedBlocks: List<String>,
    val preservedBlocks: List<String>
) {
    fun isEmpty(): Boolean = modifiedBlocks.isEmpty()

    fun isNotEmpty(): Boolean = modifiedBlocks.isNotEmpty()

    companion object {
        fun compute(
            previous: Map<String, BlockChecksumEntry>,
            current: Map<String, String>
        ): BlockDelta {
            val modified = mutableListOf<String>()
            val preserved = mutableListOf<String>()

            for ((index, currentHash) in current) {
                val previousEntry = previous[index]
                if (previousEntry == null) {
                    modified.add(index)
                } else if (previousEntry.hash != currentHash) {
                    modified.add(index)
                } else if (previousEntry.status != BlockTranslationStatus.TRANSLATED) {
                    modified.add(index)
                } else {
                    preserved.add(index)
                }
            }

            return BlockDelta(modified, preserved)
        }
    }
}