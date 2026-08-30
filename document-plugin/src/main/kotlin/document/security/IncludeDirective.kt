package document.security

/**
 * A single `include::target[opts]` occurrence discovered in an AsciiDoc
 * document (DOC-CR4). Pure value object — no I/O, no Gradle dependency.
 *
 * @param raw the full matched directive text (e.g. `include::chap1.adoc[lines=1..10]`)
 * @param target the include target path as written (quotes stripped)
 * @param line 1-based line number of the directive in the source
 */
data class IncludeDirective(
    val raw: String,
    val target: String,
    val line: Int,
)
