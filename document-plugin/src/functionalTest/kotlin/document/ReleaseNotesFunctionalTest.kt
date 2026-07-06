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

    @Test
    fun `releaseNotesGenerate produces markdown file when rendererType is markdown`() {
        setupPluginProject()
        initRepo()
        commit("feat: first feature")
        tag("v1.0.0")
        commit("fix(api): bug fix")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("markdown")
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val mdFiles = outputDir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()
        assertTrue(mdFiles.isNotEmpty(), "a .md file should be generated")
        val content = mdFiles.first().readText()
        assertTrue(content.contains("# Release Notes"))
        assertTrue(content.contains("## Nouveautés") || content.contains("## Corrections"))
    }

    @Test
    fun `releaseNotesGenerate produces json file when rendererType is json`() {
        setupPluginProject()
        initRepo()
        commit("feat: feature a")
        tag("v1.0.0")
        commit("fix: bug fix")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("json")
                    includeDownloads.set(false)
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val jsonFiles = outputDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        assertTrue(jsonFiles.isNotEmpty(), "a .json file should be generated")
        val content = jsonFiles.first().readText()
        assertTrue(content.contains("\"version\""))
        assertTrue(content.contains("\"commits\""))
    }

    @Test
    fun `releaseNotesGenerate uses custom categories from DSL`() {
        setupPluginProject()
        initRepo()
        commit("feat: new feature")
        tag("v1.0.0")
        commit("fix: bug fix")
        commit("chore: maintenance task")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    categories.set(mapOf(
                        "feat" to "New features",
                        "fix" to "Bug fixes",
                        "chore" to "Custom chores label"
                    ))
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val adocFiles = outputDir.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertTrue(adocFiles.isNotEmpty())
        val content = adocFiles.first().readText()
        assertTrue(content.contains("== Bug fixes"))
        assertTrue(content.contains("== Custom chores label"))
        assertTrue(content.contains("- maintenance task"))
    }

    @Test
    fun `releaseNotesGenerate rendererType via CLI property overrides DSL`() {
        setupPluginProject()
        initRepo()
        commit("feat: feature a")
        tag("v1.0.0")
        commit("fix: bug fix")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("asciidoc")
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate", "-Pdocument.releaseNotesRendererType=markdown")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val mdFiles = outputDir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()
        assertTrue(mdFiles.isNotEmpty(), "CLI override should produce markdown")
    }

    // --- DOC-8.3 — N3 metadata integration ---

    @Test
    fun `collectDocumentRetrieve indexes release notes in composite-context json`() {
        setupPluginProject()
        initRepo()
        commit("feat: initial feature")
        tag("v1.0.0")
        commit("fix(api): bug fix")

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val compositeFile = File(projectDir, "build/docs/document/composite-context.json")
        assertTrue(compositeFile.exists(), "composite-context.json must be produced")
        val content = compositeFile.readText()
        assertTrue(content.contains("\"releaseNotes\""), "composite-context must have releaseNotes array")
        assertTrue(content.contains("\"releaseNotesCount\""), "composite-context must have releaseNotesCount")
        assertTrue(content.contains("release-notes"), "releaseNotes entries must reference the release-notes file")
        assertTrue(content.contains("\"rendererType\""), "releaseNotes entries must carry rendererType")
        assertTrue(content.contains("\"asciidoc\""), "asciidoc rendererType must be present")
    }

    @Test
    fun `collectDocumentRetrieve includes release notes path in metadata json`() {
        setupPluginProject()
        initRepo()
        commit("feat: initial feature")
        tag("v1.0.0")
        commit("fix: bug fix")

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val metadataFile = File(projectDir, "build/docs/document/metadata.json")
        assertTrue(metadataFile.exists(), "metadata.json must be produced")
        val content = metadataFile.readText()
        assertTrue(content.contains("\"releaseNotesPath\""), "metadata must carry releaseNotesPath")
        assertTrue(content.contains("release-notes"), "releaseNotesPath must reference the release-notes file")
        assertTrue(content.contains("\"releaseNotesRenderer\""), "metadata must carry releaseNotesRenderer")
        assertTrue(content.contains("asciidoc"), "releaseNotesRenderer must be asciidoc")
    }

    @Test
    fun `collectDocumentRetrieve metadata json omits releaseNotesPath when no release notes`() {
        setupPluginProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val metadataFile = File(projectDir, "build/docs/document/metadata.json")
        assertTrue(metadataFile.exists())
        val content = metadataFile.readText()
        assertTrue(!content.contains("releaseNotesPath"), "metadata must omit releaseNotesPath when no release notes")
    }

    @Test
    fun `collectDocumentRetrieve indexes markdown release notes when rendererType is markdown`() {
        setupPluginProject()
        initRepo()
        commit("feat: initial feature")
        tag("v1.0.0")
        commit("fix: bug fix")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("markdown")
                }
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)
        val compositeFile = File(projectDir, "build/docs/document/composite-context.json")
        val content = compositeFile.readText()
        assertTrue(content.contains("\"markdown\""), "composite-context must carry markdown rendererType")
        val metadataFile = File(projectDir, "build/docs/document/metadata.json")
        assertTrue(metadataFile.readText().contains("\"markdown\""), "metadata releaseNotesRenderer must be markdown")
    }

    // --- DOC-8.4 — ollama-asciidoc renderer with IA summary ---

    @Test
    fun `releaseNotesGenerate produces asciidoc with IA summary when rendererType is ollama-asciidoc and llmMode is fake`() {
        setupPluginProject()
        initRepo()
        commit("feat: first feature")
        tag("v1.0.0")
        commit("fix(api): bug fix")
        commit("docs: update readme")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                releaseNotes {
                    rendererType.set("ollama-asciidoc")
                    llmMode.set("fake")
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseNotesGenerate", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseNotesGenerate")?.outcome)
        val outputDir = File(projectDir, "build/release-notes")
        val adocFiles = outputDir.listFiles { _, name -> name.endsWith(".adoc") } ?: emptyArray()
        assertTrue(adocFiles.isNotEmpty(), "an .adoc file should be generated")
        val content = adocFiles.first().readText()
        assertTrue(content.contains("= Release Notes"), "title must be present")
        assertTrue(content.contains("== Résumé"), "IA summary section must be present")
        assertTrue(content.contains("== Nouveautés") || content.contains("== Corrections"), "categories must be present")
        assertTrue(content.contains("bug fix (api)"), "commit messages must be present after the summary")
    }
}