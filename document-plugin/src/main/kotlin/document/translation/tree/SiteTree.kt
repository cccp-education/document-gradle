package document.translation.tree

enum class TraversalOrder { PRE_ORDER, POST_ORDER }

class SiteTree(val root: SiteNode) {

    fun walk(order: TraversalOrder = TraversalOrder.PRE_ORDER): List<SiteNode> =
        when (order) {
            TraversalOrder.PRE_ORDER -> walkPreOrder(root)
            TraversalOrder.POST_ORDER -> walkPostOrder(root)
        }

    private fun walkPreOrder(node: SiteNode): List<SiteNode> = when (node) {
        is SiteNode.Site -> listOf(node) + node.sections.flatMap { walkPreOrder(it) }
        is SiteNode.Section -> listOf(node) + node.articles.flatMap { walkPreOrder(it) }
        is SiteNode.Article -> listOf(node)
    }

    private fun walkPostOrder(node: SiteNode): List<SiteNode> = when (node) {
        is SiteNode.Site -> node.sections.flatMap { walkPostOrder(it) } + listOf(node)
        is SiteNode.Section -> node.articles.flatMap { walkPostOrder(it) } + listOf(node)
        is SiteNode.Article -> listOf(node)
    }

    fun filter(predicate: (SiteNode) -> Boolean): List<SiteNode> =
        walk().filter(predicate)

    fun leaves(): List<SiteNode.Article> =
        walk().filterIsInstance<SiteNode.Article>()

    fun sections(): List<SiteNode> =
        walk().filter { it.isSection() }

    fun findByPath(path: String): SiteNode? =
        walk().firstOrNull { it.path == path }

    fun findSubtree(path: String): SiteTree? {
        val node = findByPath(path) ?: return null
        return SiteTree(node)
    }

    fun <T> visit(transform: (SiteNode) -> T): List<T> =
        walk().map(transform)
}
