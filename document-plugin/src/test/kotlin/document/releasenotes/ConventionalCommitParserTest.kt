package document.releasenotes

import contracts.pipeline.ConventionalCommit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConventionalCommitParserTest {

    private val parser = ConventionalCommitParser()

    @Test
    fun `parse returns commit with type feat and scope for conventional message`() {
        val commit = parser.parseSubject("feat(api): add release notes endpoint", hash = "abc1234", date = "2026-07-05T10:00:00Z")

        assertNotNull(commit)
        assertEquals("feat", commit!!.type)
        assertEquals("api", commit.scope)
        assertEquals("add release notes endpoint", commit.message)
        assertEquals("abc1234", commit.hash)
        assertEquals("2026-07-05T10:00:00Z", commit.date)
    }

    @Test
    fun `parse returns commit without scope when no parentheses`() {
        val commit = parser.parseSubject("fix: correct null pointer in converter", hash = "def5678", date = "2026-07-05T11:00:00Z")

        assertNotNull(commit)
        assertEquals("fix", commit!!.type)
        assertNull(commit.scope)
        assertEquals("correct null pointer in converter", commit.message)
    }

    @Test
    fun `parse supports all conventional types`() {
        val types = listOf("feat", "fix", "chore", "perf", "refactor", "docs", "test", "ci", "build", "style")
        types.forEach { type ->
            val commit = parser.parseSubject("$type: msg", hash = "h", date = "d")
            assertNotNull(commit, "type $type should be parsed")
            assertEquals(type, commit!!.type, "type $type should be parsed")
        }
    }

    @Test
    fun `parse returns null for non-conventional commit`() {
        val commit = parser.parseSubject("updated some files", hash = "x", date = "y")
        assertNull(commit)
    }

    @Test
    fun `parse returns null for empty line`() {
        val commit = parser.parseSubject("", hash = "x", date = "y")
        assertNull(commit)
    }

    @Test
    fun `parse strips breaking change marker and keeps message`() {
        val commit = parser.parseSubject("feat(parser)!: support breaking change marker", hash = "h", date = "d")
        assertNotNull(commit)
        assertEquals("support breaking change marker", commit!!.message)
    }

    @Test
    fun `parseLines filters out non-conventional commits and keeps valid ones`() {
        val lines = listOf("feat: first feature", "random commit", "fix(core): bug fix", "")
        val commits = parser.parseLines(
            lines,
            hashes = listOf("h1", "h2", "h3", "h4"),
            dates = listOf("d1", "d2", "d3", "d4"),
        )
        assertEquals(2, commits.size)
        assertEquals("feat", commits[0].type)
        assertEquals("fix", commits[1].type)
        assertEquals("core", commits[1].scope)
    }

    @Test
    fun `parseLines returns empty list when all commits are non-conventional`() {
        val lines = listOf("random", "another random")
        val commits = parser.parseLines(lines, hashes = listOf("h1", "h2"), dates = listOf("d1", "d2"))
        assertTrue(commits.isEmpty())
    }
}