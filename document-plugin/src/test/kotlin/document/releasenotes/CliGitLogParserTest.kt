package document.releasenotes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.io.File

class CliGitLogParserTest {

    @TempDir
    lateinit var repoDir: File

    private fun initRepo(): File {
        runGit("init")
        runGit("config", "user.email", "test@example.com")
        runGit("config", "user.name", "Test")
        return repoDir
    }

    private fun runGit(vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(repoDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    private fun commit(message: String) {
        File(repoDir, "README.md").appendText("# commit $message\n")
        runGit("add", ".")
        val env = listOf("GIT_AUTHOR_DATE=2026-07-05T10:00:00", "GIT_COMMITTER_DATE=2026-07-05T10:00:00")
        val pb = ProcessBuilder("git", "commit", "-m", message).directory(repoDir)
        pb.environment()["GIT_AUTHOR_DATE"] = "2026-07-05T10:00:00"
        pb.environment()["GIT_COMMITTER_DATE"] = "2026-07-05T10:00:00"
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    private fun tag(name: String) {
        val pb = ProcessBuilder("git", "tag", name).directory(repoDir)
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    @Test
    fun `parse returns empty list when no commits`() {
        initRepo()
        val parser = CliGitLogParser(repoDir)
        val commits = parser.parse("v1.0.0", "HEAD")
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `parse returns conventional commits between two tags`() {
        initRepo()
        commit("feat: first feature")
        tag("v1.0.0")
        commit("fix(api): bug fix")
        commit("chore: maintenance")
        commit("random non conventional")
        tag("v2.0.0")

        val parser = CliGitLogParser(repoDir)
        val commits = parser.parse("v1.0.0", "v2.0.0")
        assertEquals(2, commits.size)
        assertEquals("fix", commits[0].type)
        assertEquals("api", commits[0].scope)
        assertEquals("chore", commits[1].type)
    }

    @Test
    fun `parse with empty fromTag returns all commits up to toTag`() {
        initRepo()
        commit("feat: first")
        commit("fix: second")
        val parser = CliGitLogParser(repoDir)
        val commits = parser.parse("", "HEAD")
        assertEquals(2, commits.size)
    }

    @Test
    fun `detectVersion reads VERSION file`() {
        initRepo()
        File(repoDir, "VERSION").writeText("1.2.3")
        val parser = CliGitLogParser(repoDir)
        assertEquals("1.2.3", parser.detectVersion(repoDir))
    }

    @Test
    fun `detectVersion reads version from gradle properties when no VERSION file`() {
        initRepo()
        File(repoDir, "gradle.properties").writeText("group=edu\nversion=0.5.0\n")
        val parser = CliGitLogParser(repoDir)
        assertEquals("0.5.0", parser.detectVersion(repoDir))
    }

    @Test
    fun `detectVersion returns null when no version source`() {
        initRepo()
        val parser = CliGitLogParser(repoDir)
        assertNull(parser.detectVersion(repoDir))
    }

    @Test
    fun `detectFromTag returns previous tag before toTag`() {
        initRepo()
        commit("feat: a")
        tag("v1.0.0")
        commit("fix: b")
        tag("v2.0.0")
        val parser = CliGitLogParser(repoDir)
        val fromTag = parser.detectFromTag(repoDir, "v2.0.0")
        assertEquals("v1.0.0", fromTag)
    }

    @Test
    fun `detectFromTag returns null when only one tag`() {
        initRepo()
        commit("feat: a")
        tag("v1.0.0")
        val parser = CliGitLogParser(repoDir)
        val fromTag = parser.detectFromTag(repoDir, "v1.0.0")
        assertNull(fromTag)
    }

    @Test
    fun `detectFromTag returns first tag when toTag not found`() {
        initRepo()
        commit("feat: a")
        tag("v1.0.0")
        commit("fix: b")
        tag("v2.0.0")
        val parser = CliGitLogParser(repoDir)
        val fromTag = parser.detectFromTag(repoDir, "v3.0.0")
        assertNotNull(fromTag)
        assertEquals("v2.0.0", fromTag)
    }
}