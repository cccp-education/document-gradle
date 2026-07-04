package document

/**
 * Order of a [BookPage] within the assembled book.
 *
 * [PageOrder] captures the position of a page in the original book.
 * It is derived from the leading digits of the OCR file name produced
 * by codex-gradle (e.g. `001-page.adoc` -> 1).
 *
 * Pages without a numeric prefix are ordered after numbered ones
 * (order = [Int.MAX_VALUE]), preserving alphabetic order among them
 * to keep the assembler deterministic.
 */
@JvmInline
value class PageOrder(val value: Int) : Comparable<PageOrder> {

    init {
        require(value >= 0) { "PageOrder must be non-negative, got: $value" }
    }

    override fun compareTo(other: PageOrder): Int = value.compareTo(other.value)

    companion object {

        val FIRST: PageOrder = PageOrder(0)

        private val LEADING_DIGITS = Regex("""^(\d+)""")

        /**
         * Derives a [PageOrder] from the leading digits of a file name.
         * Files without a numeric prefix are ordered after all numbered ones.
         */
        fun fromFileName(name: String): PageOrder {
            val match = LEADING_DIGITS.find(name)
            return if (match != null) {
                PageOrder(match.groupValues[1].toInt())
            } else {
                PageOrder(Int.MAX_VALUE)
            }
        }
    }
}