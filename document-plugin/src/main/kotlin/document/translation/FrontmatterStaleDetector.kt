package document.translation

data class FrontmatterStaleReport(
    val stale: Boolean,
    val staleKeys: Set<String>
)

object FrontmatterStaleDetector {

    private const val TITLE_KEY = "title"
    private val TRANSLATABLE_JBAKE_KEYS = setOf("summary", "description")
    private val TRANSLATABLE_ASCIIDOC_KEYS = setOf("summary", "description")

    fun detect(source: PivotFrontmatter, target: PivotFrontmatter): FrontmatterStaleReport {
        val staleKeys = mutableSetOf<String>()

        if (isStale(source.title, target.title)) {
            staleKeys.add(TITLE_KEY)
        }

        for (key in TRANSLATABLE_JBAKE_KEYS) {
            val srcVal = source.jbakeAttributes[key]
            val tgtVal = target.jbakeAttributes[key]
            if (isStale(srcVal, tgtVal)) {
                staleKeys.add(key)
            }
        }

        for (key in TRANSLATABLE_ASCIIDOC_KEYS) {
            val srcVal = source.asciidocAttributes[key]
            val tgtVal = target.asciidocAttributes[key]
            if (isStale(srcVal, tgtVal)) {
                staleKeys.add(key)
            }
        }

        return FrontmatterStaleReport(stale = staleKeys.isNotEmpty(), staleKeys = staleKeys)
    }

    private fun isStale(sourceValue: String?, targetValue: String?): Boolean {
        if (sourceValue.isNullOrBlank()) return false
        if (targetValue.isNullOrBlank()) return false
        return sourceValue == targetValue
    }
}