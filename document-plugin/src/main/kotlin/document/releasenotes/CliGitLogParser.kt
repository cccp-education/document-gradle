package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.GitLogParser
import java.io.File

/**
 * CLI-based [GitLogParser] — shells out to `git log` via [ProcessBuilder].
 *
 * The repository directory is injected via [projectDir] so this parser is
 * testable against a temporary fixture repo (no production coupling to the
 * working directory).
 *
 * Format used : `git log --format=%s%x09%H%x09%aI` (subject<TAB>hash<TAB>date).
 * The TAB separator (x09) avoids collisions with user content.
 */
class CliGitLogParser(
    private val projectDir: File,
    private val commitParser: ConventionalCommitParser = ConventionalCommitParser(),
) : GitLogParser {

    override fun parse(fromTag: String, toTag: String): List<ConventionalCommit> {
        val range = if (fromTag.isBlank()) toTag else "$fromTag..$toTag"
        val output = runGit("log", "--format=%s%x09%H%x09%aI", range)
        if (output.isBlank()) return emptyList()
        val lines = output.lines().filter { it.isNotBlank() }
        val subjects = ArrayList<String>(lines.size)
        val hashes = ArrayList<String>(lines.size)
        val dates = ArrayList<String>(lines.size)
        lines.forEach { line ->
            val parts = line.split("\t")
            if (parts.size >= 3) {
                subjects.add(parts[0])
                hashes.add(parts[1])
                dates.add(parts[2])
            }
        }
        return commitParser.parseLines(subjects, hashes, dates).asReversed()
    }

    override fun detectVersion(projectDir: File): String? {
        val versionFile = projectDir.resolve("VERSION")
        if (versionFile.isFile) return versionFile.readText().trim().ifBlank { null }
        val gradleProperties = projectDir.resolve("gradle.properties")
        if (gradleProperties.isFile) {
            val content = gradleProperties.readText()
            val match = Regex("^version\\s*=\\s*(.+)$", RegexOption.MULTILINE).find(content)
            if (match != null) return match.groupValues[1].trim().ifBlank { null }
        }
        return null
    }

    override fun detectFromTag(projectDir: File, toTag: String): String? {
        val tags = runGit("tag", "--sort=-v:refname").lines().filter { it.isNotBlank() }
        if (tags.isEmpty()) return null
        val toIndex = tags.indexOf(toTag)
        if (toIndex == -1) return tags.firstOrNull()
        return tags.getOrNull(toIndex + 1)
    }

    private fun runGit(vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(projectDir)
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) return ""
        return stdout
    }
}