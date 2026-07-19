package document.translation.tree

data class OutputConfig(
    val template: String? = null,
    val layout: LayoutType? = null,
    val cssFiles: List<String>? = null,
    val jsFiles: List<String>? = null,
    val assets: PageAssets? = null,
    val theme: ThemeConfig? = null
)
