package document

/**
 * Previous / next navigation around a book section, in document order.
 *
 * [BookNavigation] is the value object returned by [BookNumbering.navigation]:
 * it carries the [BookSection] that precedes and the one that follows the
 * section identified by a `ref` in the [BookTree.leaves] order (the physical
 * page order of the assembled book). Either side may be `null` at the
 * boundaries of the book.
 *
 * Ink Economy Law: the navigation is a pure function of the tree — no I/O,
 * fully deterministic and testable in isolation.
 */
data class BookNavigation(
    val previous: BookSection?,
    val next: BookSection?,
)
