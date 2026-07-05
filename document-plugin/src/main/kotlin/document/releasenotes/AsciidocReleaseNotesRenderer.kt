package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesRenderer
import java.io.File

/**
 * AsciiDoc renderer — formats a list of [ConventionalCommit] as a structured
 * AsciiDoc document grouped by category (feat → "Nouveautés", fix → ...).
 *
 * Sections :
 *   - Document title with version
 *   - One section per category present in the commits, in config order
 *   - Optional "Téléchargement" section when [ReleaseNotesConfig.includeDownloads]
 */
class AsciidocReleaseNotesRenderer : ReleaseNotesRenderer {

    override val format: String = "asciidoc"

    override fun render(commits: List<ConventionalCommit>, config: ReleaseNotesConfig): String {
        if (commits.isEmpty()) return ""
        val version = config.version ?: "SNAPSHOT"
        val sb = StringBuilder()
        sb.append("= Release Notes $version\n\n")
        val grouped = commits.groupBy { it.type }
        config.categories.forEach { (type, label) ->
            val typeCommits = grouped[type] ?: return@forEach
            sb.append("== $label\n\n")
            typeCommits.forEach { c ->
                val scope = c.scope?.let { " ($it)" } ?: ""
                sb.append("- ${c.message}$scope\n")
            }
            sb.append("\n")
        }
        if (config.includeDownloads) {
            sb.append("== Téléchargement\n\n")
            sb.append("Lien Maven Central et badges à compléter selon le borough.\n")
        }
        return sb.toString()
    }

    override fun renderToFile(commits: List<ConventionalCommit>, config: ReleaseNotesConfig, outputFile: File): File {
        val content = render(commits, config)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(content)
        return outputFile
    }
}