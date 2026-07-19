package document.translation.tree

data class PageAssets(
    val css: List<AssetRef>? = null,
    val js: List<AssetRef>? = null
) {
    fun merge(parent: PageAssets?): PageAssets {
        if (parent == null) return this
        return PageAssets(
            css = css ?: parent.css,
            js = js ?: parent.js
        )
    }
}
