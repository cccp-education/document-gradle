package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesRenderer
import java.io.File

/**
 * JSON renderer — formats a list of [ConventionalCommit] as a structured JSON
 * document (DOC-8.2). The schema is stable and machine-readable so that
 * downstream consumers (runner-gradle N3, dashboards) can parse release notes
 * without scraping AsciiDoc/Markdown.
 *
 * Schema :
 * ```json
 * {
 *   "version": "1.2.0",
 *   "commits": [ { "type": "feat", "scope": "api", "message": "...", "hash": "...", "date": "..." }, ... ],
 *   "categories": { "feat": "Nouveautés", ... },
 *   "includeDownloads": true
 * }
 * ```
 */
class JsonReleaseNotesRenderer : ReleaseNotesRenderer {

    override val format: String = "json"

    override fun render(commits: List<ConventionalCommit>, config: ReleaseNotesConfig): String {
        val version = config.version ?: "SNAPSHOT"
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"version\":\"").append(escape(version)).append("\"")
        sb.append(",\"commits\":[")
        commits.forEachIndexed { index, c ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"type\":\"").append(escape(c.type)).append("\"")
            sb.append(",\"scope\":").append(c.scope?.let { "\"${escape(it)}\"" } ?: "null")
            sb.append(",\"message\":\"").append(escape(c.message)).append("\"")
            sb.append(",\"hash\":\"").append(escape(c.hash)).append("\"")
            sb.append(",\"date\":\"").append(escape(c.date)).append("\"")
            sb.append("}")
        }
        sb.append("]")
        sb.append(",\"categories\":{")
        config.categories.entries.forEachIndexed { index, (type, label) ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(escape(type)).append("\":\"").append(escape(label)).append("\"")
        }
        sb.append("}")
        if (config.includeDownloads) {
            sb.append(",\"includeDownloads\":true")
        }
        sb.append("}")
        return sb.toString()
    }

    override fun renderToFile(commits: List<ConventionalCommit>, config: ReleaseNotesConfig, outputFile: File): File {
        val content = render(commits, config)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(content)
        return outputFile
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}