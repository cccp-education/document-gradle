package document.validation

/**
 * Pure DDD service linting internal links of a rendered HTML document
 * (`convertDocumentToHtml`).
 *
 * The linter scans the raw HTML text and reconciles two sets :
 * - *defined anchors* : `id="..."` / `id='...'` attributes and legacy `name="..."`
 *   anchors ;
 * - *internal links* : `href="#..."` / `href='#...'` fragment-only links.
 *
 * Any internal link whose fragment is not a defined anchor (and is non-empty) is
 * reported as dead. A navigable book also exposes a table of contents, detected via
 * the `id="toc"` anchor emitted by [document.book.BookLayout.tableOfContents].
 *
 * Ink Economy Law: every method is a pure deterministic function of the input HTML
 * — no I/O, no Gradle dependency, fully unit-testable in isolation. The heuristic is
 * intentionally textual (parallel to [document.xref.XrefValidator]), which is
 * sufficient for the HTML AsciidoctorJ produces where anchors are explicit.
 */
object HtmlLinkLinter {

    private val ID_DOUBLE = Regex("""(?i)(?<![a-zA-Z0-9_-])id\s*=\s*"([^"]*)"""")
    private val ID_SINGLE = Regex("""(?i)(?<![a-zA-Z0-9_-])id\s*=\s*'([^']*)'""")
    private val NAME_DOUBLE = Regex("""(?i)(?<![a-zA-Z0-9_-])name\s*=\s*"([^"]*)"""")
    private val NAME_SINGLE = Regex("""(?i)(?<![a-zA-Z0-9_-])name\s*=\s*'([^']*)'""")
    private val HREF_DOUBLE = Regex("""(?i)(?<![a-zA-Z0-9_-])href\s*=\s*"#([^"]*)"""")
    private val HREF_SINGLE = Regex("""(?i)(?<![a-zA-Z0-9_-])href\s*=\s*'#([^']*)'""")
    private val TOC = Regex("""(?i)(?<![a-zA-Z0-9_-])id\s*=\s*["']toc["']""")

    /**
     * Extracts the set of defined anchor ids in [html] (id + name attributes,
     * deduplicated).
     */
    fun extractAnchors(html: String): Set<String> {
        val ids = ID_DOUBLE.findAll(html).map { it.groupValues[1] } +
            ID_SINGLE.findAll(html).map { it.groupValues[1] }
        val names = NAME_DOUBLE.findAll(html).map { it.groupValues[1] } +
            NAME_SINGLE.findAll(html).map { it.groupValues[1] }
        return (ids + names).toSet()
    }

    /**
     * Extracts the list of internal link fragments in [html], in document order
     * (empty fragments `#` excluded — they point to the top of the page).
     */
    fun extractInternalLinks(html: String): List<String> {
        val double = HREF_DOUBLE.findAll(html).map { it.groupValues[1] }
        val single = HREF_SINGLE.findAll(html).map { it.groupValues[1] }
        return (double + single).filter { it.isNotBlank() }.toList()
    }

    /**
     * Detects whether [html] exposes a table of contents anchor (`id="toc"`),
     * an indicator of navigability for a generated book.
     */
    fun hasTableOfContents(html: String): Boolean = TOC.containsMatchIn(html)

    /**
     * Validates [html] : returns [HtmlLinkLintResult.Valid] when every internal link
     * resolves to a defined anchor, otherwise [HtmlLinkLintResult.Invalid] carrying
     * the sorted, deduplicated list of dead fragments.
     */
    fun validate(html: String): HtmlLinkLintResult {
        val defined = extractAnchors(html)
        val dead = extractInternalLinks(html)
            .filter { it !in defined }
            .distinct()
            .sorted()
        return if (dead.isEmpty()) {
            HtmlLinkLintResult.Valid
        } else {
            HtmlLinkLintResult.Invalid(dead)
        }
    }
}
