package document.a11y

import java.io.File

/**
 * DOC-A11Y — audits the generated artifacts directory.
 *
 * HTML and EPUB are inspected; PDF is not a blind-readable deliverable
 * (Asciidoctor PDF is not PDF/UA), so it is reported with an explicit
 * `pdf-not-tagged` warning and a declared limit. The report is the producer's
 * contract: academy consumes it and never re-implements these checks.
 */
object DocumentA11yAuditor {
    /** The limits every report declares. */
    val LIMITS: List<String> = listOf("PDF is not PDF/UA; the accessible text deliverable is HTML/EPUB")

    /** Audits every HTML/EPUB/PDF file of [artifactsDir]. */
    fun audit(artifactsDir: File?): DocumentA11yReport {
        if (artifactsDir == null || !artifactsDir.isDirectory) return DocumentA11yReport()

        val files =
            artifactsDir
                .listFiles { file -> file.isFile }
                ?.sortedBy { it.name }
                .orEmpty()
        val deliverables = mutableListOf<DeliverableA11yReport>()

        files.filter { it.extension.equals("html", ignoreCase = true) }.forEach { file ->
            deliverables +=
                DeliverableA11yReport(
                    kind = "HTML",
                    findings = HtmlAccessibilityInspector.inspect(file.readText(), file.name),
                )
        }
        files.filter { it.extension.equals("epub", ignoreCase = true) }.forEach { file ->
            deliverables +=
                DeliverableA11yReport(
                    kind = "EPUB",
                    findings = EpubAccessibilityInspector.inspect(file, file.name),
                )
        }
        files.filter { it.extension.equals("pdf", ignoreCase = true) }.forEach { file ->
            deliverables +=
                DeliverableA11yReport(
                    kind = "PDF",
                    findings =
                        listOf(
                            a11yFinding(
                                DocumentA11yRules.PDF_NOT_TAGGED,
                                DocumentA11ySeverity.WARNING,
                                file.name,
                                "PDF is not PDF/UA and cannot be declared accessible",
                            ),
                        ),
                    limits = LIMITS,
                )
        }

        return DocumentA11yReport(deliverables = deliverables)
    }
}
