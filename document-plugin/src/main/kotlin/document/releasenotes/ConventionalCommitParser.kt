package document.releasenotes

import contracts.pipeline.ConventionalCommit

/**
 * Parser for Conventional Commits subjects — extracts a [ConventionalCommit]
 * from a single git log subject line.
 *
 * Pure component (no I/O) — the [CliGitLogParser] is responsible for invoking
 * `git log` and feeding subject/hash/date lines into [parseLines].
 *
 * Format supported (Conventional Commits 1.0.0):
 *   `type(scope): message`
 *   `type: message`
 *
 * Non-conventional lines return null (filtered out by [parseLines]).
 */
class ConventionalCommitParser {

    private val regex = Regex("^(feat|fix|chore|perf|refactor|docs|test|ci|build|style)(?:\\(([^)]+)\\))?!?: (.+)$")

    fun parseSubject(subject: String, hash: String, date: String): ConventionalCommit? {
        if (subject.isBlank()) return null
        val match = regex.matchEntire(subject) ?: return null
        val type = match.groupValues[1]
        val scope = match.groupValues[2].takeIf { it.isNotBlank() }
        val message = match.groupValues[3]
        return ConventionalCommit(type = type, scope = scope, message = message, hash = hash, date = date)
    }

    fun parseLines(subjects: List<String>, hashes: List<String>, dates: List<String>): List<ConventionalCommit> {
        require(subjects.size == hashes.size && subjects.size == dates.size) {
            "subjects, hashes and dates must have the same size"
        }
        return subjects.indices.mapNotNull { i ->
            parseSubject(subjects[i], hashes[i], dates[i])
        }
    }
}