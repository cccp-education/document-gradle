package document.translation.tree

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

sealed interface SiteNode {
    val path: String

    val metadata: NodeMetadata?
    val outputConfig: OutputConfig?

    fun isLeaf(): Boolean
    fun isSection(): Boolean

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Site(
        override val path: String,
        val sections: List<Section>,
        override val metadata: NodeMetadata? = null,
        override val outputConfig: OutputConfig? = null
    ) : SiteNode {
        override fun isLeaf(): Boolean = false
        override fun isSection(): Boolean = true
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Section(
        override val path: String,
        val articles: List<Article>,
        override val metadata: NodeMetadata? = null,
        override val outputConfig: OutputConfig? = null
    ) : SiteNode {
        init {
            require(path.isNotBlank()) { "Section path must not be blank" }
        }

        override fun isLeaf(): Boolean = false
        override fun isSection(): Boolean = true
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Article(
        override val path: String,
        @JsonIgnore
        val content: Content? = null,
        override val metadata: NodeMetadata? = null,
        override val outputConfig: OutputConfig? = null
    ) : SiteNode {
        init {
            require(path.isNotBlank()) { "Article path must not be blank" }
        }

        override fun isLeaf(): Boolean = true
        override fun isSection(): Boolean = false
    }
}
