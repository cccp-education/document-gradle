package document.a11y

import java.io.File
import java.util.zip.ZipFile

/**
 * DOC-A11Y — inspects the EPUB binary for the WCAG signals the producer owns.
 *
 * The EPUB is a ZIP: every XHTML content document is checked with the same
 * structural signals as HTML (`lang`, `alt`, `th`), aggregated per deliverable,
 * and the OPF is checked for the EPUB 3 accessibility metadata
 * (`schema:accessMode` / `accessibilityFeature`).
 *
 * A malformed or unreadable archive is a declared `WARNING`, never a crash: the
 * producer reports its limits, the consumer aggregates them.
 */
object EpubAccessibilityInspector {
    /** Findings for one EPUB file. */
    fun inspect(
        epub: File,
        location: String,
    ): List<DocumentA11yFinding> {
        val findings = mutableListOf<DocumentA11yFinding>()
        try {
            ZipFile(epub).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val content = entries.filter { it.name.endsWith(".xhtml") || it.name.endsWith(".html") }
                if (content.isEmpty()) {
                    findings +=
                        a11yFinding(
                            DocumentA11yRules.EPUB_UNREADABLE,
                            DocumentA11ySeverity.WARNING,
                            location,
                            "the EPUB carries no XHTML content",
                        )
                } else {
                    val documents = content.map { entry -> zip.getInputStream(entry).bufferedReader().readText() }
                    if (documents.none { HtmlSignals.hasLang(it) }) {
                        findings +=
                            a11yFinding(
                                DocumentA11yRules.EPUB_LANG_MISSING,
                                DocumentA11ySeverity.ERROR,
                                location,
                                "no XHTML document declares a lang attribute",
                            )
                    }
                    val images = documents.sumOf { HtmlSignals.imagesWithoutAlt(it) }
                    if (images > 0) {
                        findings +=
                            a11yFinding(
                                DocumentA11yRules.EPUB_IMAGE_ALT_MISSING,
                                DocumentA11ySeverity.ERROR,
                                location,
                                "$images image(s) without alt",
                            )
                    }
                    val tables = documents.count { HtmlSignals.hasTableWithoutHeader(it) }
                    if (tables > 0) {
                        findings +=
                            a11yFinding(
                                DocumentA11yRules.EPUB_TABLE_HEADER_MISSING,
                                DocumentA11ySeverity.WARNING,
                                location,
                                "$tables table(s) without a th header cell",
                            )
                    }
                }

                val metadata =
                    entries
                        .firstOrNull { it.name.endsWith(".opf") }
                        ?.let { entry -> zip.getInputStream(entry).bufferedReader().readText() }
                        .orEmpty()
                if (!metadata.contains("accessMode") && !metadata.contains("accessibilityFeature")) {
                    findings +=
                        a11yFinding(
                            DocumentA11yRules.EPUB_ACCESSIBILITY_METADATA_MISSING,
                            DocumentA11ySeverity.WARNING,
                            location,
                            "the OPF declares no schema:accessMode/accessibilityFeature metadata",
                        )
                }
            }
        } catch (exception: Exception) {
            findings +=
                a11yFinding(
                    DocumentA11yRules.EPUB_UNREADABLE,
                    DocumentA11ySeverity.WARNING,
                    location,
                    "the EPUB is unreadable: ${exception.message}",
                )
        }
        return findings
    }
}
