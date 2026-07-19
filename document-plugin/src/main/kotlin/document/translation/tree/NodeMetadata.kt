package document.translation.tree

data class NodeMetadata(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val layout: LayoutType? = null
)
