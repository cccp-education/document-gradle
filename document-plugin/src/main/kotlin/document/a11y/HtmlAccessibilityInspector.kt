package document.a11y

/**
 * DOC-A11Y — structural HTML signals shared by the HTML and EPUB inspectors.
 *
 * Regex-based on the rendered output (no DOM library): deterministic and
 * sufficient for the producer-level checks. `alt=""` is accepted (a decorative
 * image declares its intent with an empty alt); only a **missing** attribute is
 * an error.
 */
internal object HtmlSignals {
    private val HTML_TAG = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val LANG = Regex("\\blang\\s*=", RegexOption.IGNORE_CASE)
    private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ALT = Regex("\\balt\\s*=", RegexOption.IGNORE_CASE)
    private val TABLE = Regex("<table\\b", RegexOption.IGNORE_CASE)
    private val TH = Regex("<th\\b", RegexOption.IGNORE_CASE)
    private val H1 = Regex("<h1\\b", RegexOption.IGNORE_CASE)

    fun hasLang(html: String): Boolean {
        val htmlTag = HTML_TAG.find(html)?.value ?: return false
        return LANG.containsMatchIn(htmlTag)
    }

    fun imagesWithoutAlt(html: String): Int = IMG.findAll(html).count { !ALT.containsMatchIn(it.value) }

    fun hasTableWithoutHeader(html: String): Boolean = TABLE.containsMatchIn(html) && !TH.containsMatchIn(html)

    fun hasH1(html: String): Boolean = H1.containsMatchIn(html)
}

/**
 * DOC-A11Y — inspects a rendered HTML document for the text-level WCAG signals
 * the producer owns.
 *
 * Producer checks: `lang` on `<html>`, `alt` on every `<img>`, a `<th>` header
 * when a table exists, and the presence of an `<h1>`. The consumer (academy)
 * aggregates these findings; it never re-implements them.
 */
object HtmlAccessibilityInspector {
    /** Findings for one rendered HTML document. */
    fun inspect(
        html: String,
        location: String,
    ): List<DocumentA11yFinding> {
        val findings = mutableListOf<DocumentA11yFinding>()

        if (!HtmlSignals.hasLang(html)) {
            findings +=
                a11yFinding(
                    DocumentA11yRules.HTML_LANG_MISSING,
                    DocumentA11ySeverity.ERROR,
                    location,
                    "the html element has no lang attribute",
                )
        }

        val images = HtmlSignals.imagesWithoutAlt(html)
        if (images > 0) {
            findings +=
                a11yFinding(
                    DocumentA11yRules.HTML_IMAGE_ALT_MISSING,
                    DocumentA11ySeverity.ERROR,
                    location,
                    "$images image(s) without alt",
                )
        }

        if (HtmlSignals.hasTableWithoutHeader(html)) {
            findings +=
                a11yFinding(
                    DocumentA11yRules.HTML_TABLE_HEADER_MISSING,
                    DocumentA11ySeverity.WARNING,
                    location,
                    "a table has no th header cell",
                )
        }

        if (!HtmlSignals.hasH1(html)) {
            findings +=
                a11yFinding(
                    DocumentA11yRules.HTML_H1_MISSING,
                    DocumentA11ySeverity.WARNING,
                    location,
                    "no h1 heading",
                )
        }

        return findings
    }
}
