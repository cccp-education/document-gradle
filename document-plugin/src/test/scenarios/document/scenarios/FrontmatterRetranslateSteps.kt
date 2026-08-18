package document.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files

class FrontmatterRetranslateSteps(private val world: DocumentWorld) {

    @Given("a frontmatter re-translation project with fake LLM")
    fun frontmatterRetranslateProject() {
        val dir = Files.createTempDirectory("doc-bdd-retranslate").toFile()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            """.trimIndent()
        )
        world.projectDir = dir
    }

    @Given("a source FR article with jbake summary {string}")
    fun sourceFrArticleWithJbakeSummary(summary: String) {
        writeSourceFrArticle(
            title = "Titre FR",
            extraJbakeAttrs = mapOf("summary" to summary),
            body = "Corps de l article en francais."
        )
    }

    @Given("a source FR article with asciidoc summary {string}")
    fun sourceFrArticleWithAsciidocSummary(summary: String) {
        writeSourceFrArticle(
            title = "Titre FR",
            asciidocAttrs = mapOf("summary" to summary),
            body = "Corps de l article en francais."
        )
    }

    @Given("a source FR article with title {string}")
    fun sourceFrArticleWithTitle(title: String) {
        writeSourceFrArticle(
            title = title,
            body = "Corps de l article en francais."
        )
    }

    @Given("a source FR article with jbake description {string}")
    fun sourceFrArticleWithJbakeDescription(description: String) {
        writeSourceFrArticle(
            title = "Titre FR",
            extraJbakeAttrs = mapOf("description" to description),
            body = "Corps de l article en francais."
        )
    }

    @Given("a target EN article with the same jbake summary {string} and an already translated body")
    fun targetEnArticleWithSameJbakeSummary(summary: String) {
        writeTargetEnArticle(
            title = "Title EN",
            extraJbakeAttrs = mapOf("summary" to summary),
            body = "Body already translated in English."
        )
    }

    @Given("a target EN article with the same asciidoc summary {string} and an already translated body")
    fun targetEnArticleWithSameAsciidocSummary(summary: String) {
        writeTargetEnArticle(
            title = "Title EN",
            asciidocAttrs = mapOf("summary" to summary),
            body = "Body already translated in English."
        )
    }

    @Given("a target EN article with the same title {string} and an already translated body")
    fun targetEnArticleWithSameTitle(title: String) {
        writeTargetEnArticle(
            title = title,
            body = "Body already translated in English."
        )
    }

    @Given("a target EN article with the same jbake description {string} and an already translated body")
    fun targetEnArticleWithSameJbakeDescription(description: String) {
        writeTargetEnArticle(
            title = "Title EN",
            extraJbakeAttrs = mapOf("description" to description),
            body = "Body already translated in English."
        )
    }

    @Given("a target EN article with a translated jbake summary {string} and an already translated body")
    fun targetEnArticleWithTranslatedJbakeSummary(summary: String) {
        writeTargetEnArticle(
            title = "Title EN",
            extraJbakeAttrs = mapOf("summary" to summary),
            body = "Body already translated in English."
        )
    }

    @Given("a source FR article with jbake summary {string} and jbake description {string}")
    fun sourceFrArticleWithJbakeSummaryAndDescription(summary: String, description: String) {
        writeSourceFrArticle(
            title = "Titre FR",
            extraJbakeAttrs = mapOf("summary" to summary, "description" to description),
            body = "Corps de l article en francais."
        )
    }

    @Given("a target EN article with the same jbake summary {string} and same jbake description {string} and an already translated body")
    fun targetEnArticleWithSameJbakeSummaryAndDescription(summary: String, description: String) {
        writeTargetEnArticle(
            title = "Title EN",
            extraJbakeAttrs = mapOf("summary" to summary, "description" to description),
            body = "Body already translated in English."
        )
    }

    @Then("the retranslated target should contain {string}")
    fun retranslatedTargetShouldContain(expected: String) {
        val target = world.projectDir!!.resolve("content-i18n/en/article.adoc")
        assertThat(target).exists()
        assertThat(target.readText()).contains(expected)
    }

    @Then("the retranslated target should start with {string}")
    fun retranslatedTargetShouldStartWith(expected: String) {
        val target = world.projectDir!!.resolve("content-i18n/en/article.adoc")
        assertThat(target).exists()
        assertThat(target.readText().lines().firstOrNull()).isEqualTo(expected)
    }

    @Then("the retranslated target should preserve the already translated body")
    fun retranslatedTargetShouldPreserveBody() {
        val target = world.projectDir!!.resolve("content-i18n/en/article.adoc")
        assertThat(target).exists()
        val content = target.readText()
        assertThat(content).contains("Body already translated in English.")
    }

    private fun writeSourceFrArticle(
        title: String,
        extraJbakeAttrs: Map<String, String> = emptyMap(),
        asciidocAttrs: Map<String, String> = emptyMap(),
        body: String
    ) {
        val dir = world.projectDir!!
        val srcDir = dir.resolve("content/blog").apply { mkdirs() }
        val sb = StringBuilder()
        sb.appendLine("= $title")
        sb.appendLine("@CherOliv")
        sb.appendLine("2026-01-01")
        sb.appendLine(":jbake-title: $title")
        sb.appendLine(":jbake-type: post")
        sb.appendLine(":jbake-status: published")
        sb.appendLine(":jbake-date: 2026-01-01")
        for ((k, v) in extraJbakeAttrs) sb.appendLine(":jbake-$k: $v")
        for ((k, v) in asciidocAttrs) sb.appendLine(":$k: $v")
        sb.appendLine()
        sb.appendLine(body)
        srcDir.resolve("article.adoc").writeText(sb.toString())
    }

    private fun writeTargetEnArticle(
        title: String,
        extraJbakeAttrs: Map<String, String> = emptyMap(),
        asciidocAttrs: Map<String, String> = emptyMap(),
        body: String
    ) {
        val dir = world.projectDir!!
        val tgtDir = dir.resolve("content-i18n/en").apply { mkdirs() }
        val sb = StringBuilder()
        sb.appendLine("= $title")
        sb.appendLine("@CherOliv")
        sb.appendLine("2026-01-01")
        sb.appendLine(":jbake-title: $title")
        sb.appendLine(":jbake-type: post")
        sb.appendLine(":jbake-status: published")
        sb.appendLine(":jbake-date: 2026-01-01")
        for ((k, v) in extraJbakeAttrs) sb.appendLine(":jbake-$k: $v")
        for ((k, v) in asciidocAttrs) sb.appendLine(":$k: $v")
        sb.appendLine()
        sb.appendLine(body)
        tgtDir.resolve("article.adoc").writeText(sb.toString())

        val buildGradle = dir.resolve("build.gradle.kts")
        buildGradle.writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                translation {
                    batchSourceDir.set("content/blog")
                    batchOutputDir.set("content-i18n")
                    sourceLanguage.set("fr")
                    targetLanguage.set("en")
                    llmMode.set("fake")
                }
            }
            """.trimIndent()
        )
    }
}