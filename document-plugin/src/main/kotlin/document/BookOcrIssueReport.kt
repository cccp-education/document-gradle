package document

import java.io.File

/**
 * Serialises a list of [BookOcrIssue]s to a small, deterministic JSON report so a
 * human (or a downstream tool) can iterate on OCR / LLM-vision failures without
 * re-reading the whole book. The format is intentionally hand-rolled (no extra
 * dependency) — it is a flat array of localised failure objects:
 *
 * ```json
 * [
 *   { "page": 73, "file": "073.adoc", "sectionRef": "2.1.1",
 *     "sectionTitle": "Numerique et chronobiologie", "reason": "ILLISIBLE" }
 * ]
 * ```
 *
 * Ink Economy Law: pure serialisation, no I/O side effects beyond the single
 * [write] target, deterministic field order.
 */
object BookOcrIssueReport {

    fun write(issues: List<BookOcrIssue>, file: File) {
        val json =
            buildString {
                append("[\n")
                issues.forEachIndexed { index, issue ->
                    append("  {")
                    append("\"page\": ${issue.page}, ")
                    append("\"file\": ${quote(issue.file)}, ")
                    append("\"sectionRef\": ${issue.sectionRef?.let { quote(it) } ?: "null"}, ")
                    append(
                        "\"sectionTitle\": ${issue.sectionTitle?.let { quote(it) } ?: "null"}, ",
                    )
                    append("\"reason\": ${quote(issue.reason.name)}")
                    append("}")
                    if (index < issues.lastIndex) append(",")
                    append("\n")
                }
                append("]\n")
            }
        file.writeText(json)
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
