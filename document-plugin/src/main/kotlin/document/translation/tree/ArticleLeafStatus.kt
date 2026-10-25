package document.translation.tree

/**
 * GBL-010 G-2b — the status of a leaf in the article AST.
 *
 * The tree owns the *report* vocabulary; the facts that decide a PlantUML leaf
 * status come from the N0 plantuml contracts ([PlantUmlLeafStatusResolver]).
 * `document` never re-implements the diagram classifier or the syntax check.
 *
 * - [MISSING]: the block carries no source at all.
 * - [PENDING]: the diagram has translatable labels and still needs the plantuml
 *   borough to act on it.
 * - [TRANSLATED]: nothing is pending (the N0 classifier decided to preserve the
 *   block), so an ink-economy re-run must not touch it.
 * - [BROKEN]: the N0 syntax validator rejected the block; it must be repaired
 *   before any translation can be trusted.
 */
enum class ArticleLeafStatus {
    MISSING,
    PENDING,
    TRANSLATED,
    BROKEN,
}
