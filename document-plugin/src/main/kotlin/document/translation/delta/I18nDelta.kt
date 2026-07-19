package document.translation.delta

import document.translation.tree.SiteNode

data class ArticleModification(
    val path: String,
    val oldChecksum: String?,
    val newChecksum: String,
    val translatableSegmentCount: Int
)

data class I18nDelta(
    val modifiedArticles: List<ArticleModification>,
    val untouchedArticles: List<SiteNode.Article>,
    val updatedChecksums: Map<String, String>
) {
    fun isEmpty(): Boolean = modifiedArticles.isEmpty()
}
