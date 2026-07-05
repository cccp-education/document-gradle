package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class ReleaseNotesFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupPluginProject() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-releasenotes\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
    }

    private fun runGit(vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    private fun initRepo() {
        runGit("init")
        runGit("config", "user.email", "test@example.com")
        runGit("config", "user.name", "Test")
    }

    private fun commit(message: String) {
        File(projectDir, "README.md").appendText("# $message\n")
        val pb = ProcessBuilder("git", "commit", "-m", message).directory(projectDir)
        pb.environment()["GIT_AUTHOR_DATE"] = "2026-07-05T10:00:00"
        pb.environment()["GIT_COMMITTER_DATE"] = "2026-07-05T10:00:00"
        runGit("add", ".")
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    private fun tag(name: String) {
        val p = ProcessBuilder("git", "tag", name).directory(projectDir).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    }

    @Test
    fun `releaseNotesGenerate task is registered`() {
        setupPluginProject()
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--group", "document")
            .withPluginClasspath()
            .build()
        assertTrue(result.output.contains("releaseNotesGenerate"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `releaseNotesGenerate produces adoc file from conventional commits`() {
        setupPluginProject()
        initRepo()
        commit("feat: first feature")
        tag("v1.0.0")
        commit("fix(api): bug fix")
        commit("docs: update readme")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        assertTrue(outputDir.isDirectory, "output dir should exist")
        val adocFiles = outputDir.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertTrue(adocFiles.isNotEmpty(), "an .adoc file should be generated")
        val content = adocFiles.first().readText()
        assertTrue(content.contains("= Release Notes"))
        assertTrue(content.contains("== Nouveautés") || content.contains("== Corrections") || content.contains("== Documentation"))
        assertTrue(content.contains("bug fix (api)"))
    }

    @Test
    fun `releaseNotesGenerate with empty repo produces SNAPSHOT file`() {
        setupPluginProject()
        initRepo()
        commit("feat: initial")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val adocFiles = outputDir.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertTrue(adocFiles.any { it.name.contains("SNAPSHOT") })
    }

    @Test
    fun `releaseNotesGenerate reads VERSION file as version`() {
        setupPluginProject()
        initRepo()
        File(projectDir, "VERSION").writeText("2.1.0")
        commit("feat: feature a")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val adocFiles = outputDir.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertTrue(adocFiles.any { it.name.contains("2.1.0") })
    }
}