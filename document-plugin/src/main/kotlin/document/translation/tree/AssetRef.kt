package document.translation.tree

data class AssetRef(
    val path: String,
    val integrity: String? = null,
    val async: Boolean? = null,
    val defer: Boolean? = null
)
