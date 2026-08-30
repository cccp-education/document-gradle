package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import document.validation.HtmlLinkLinter
import document.validation.HtmlLinkLintMode
import document.validation.HtmlLinkLintResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Lints the HTML document produced by [convertDocumentToHtml] for navigability.
 */
class LintHtmlDocumentTask : DefaultTask() {

    private val logger: Logger = Logging.getLogger(this::class.java)

    /**
     * The HTML file to lint. This is set by the plugin to the output of [convertDocumentToHtml].
     */
    @get:InputFile
    lateinit var htmlFile: RegularFileProperty

    /**
     * The mode of linting (OFF, LENIENT, STRICT). This is set by the plugin from the converter block.
     */
    lateinit var htmlLinkLintMode: Property<HtmlLinkLintMode>

    @TaskAction
    fun lintHtmlDocument() {
        val mode = htmlLinkLintMode.get()
        when (mode) {
            HtmlLinkLintMode.OFF -> {
                logger.lifecycle("HTML link linting is disabled (OFF).")
                return
            }
            HtmlLinkLintMode.LENIENT, HtmlLinkLintMode.STRICT -> {
                val html = htmlFile.get().getAsFile().readText()
                val result = HtmlLinkLinter.validate(html)
                when (result) {
                    is HtmlLinkLintResult.Valid -> {
                        logger.lifecycle("HTML link linting passed: no dead internal links found.")
                    }
                    is HtmlLinkLintResult.Invalid -> {
                        val invalid = result as HtmlLinkLintResult.Invalid
                        val deadLinks = invalid.deadLinks.joinToString { "\"$it\"" }
                        val message = "HTML link linting failed: dead internal link(s) found: $deadLinks"
                        when (mode) {
                            HtmlLinkLintMode.LENIENT -> {
                                logger.warn(message)
                                // In LENIENT mode, we only warn, so we do not fail the task.
                            }
                            HtmlLinkLintMode.STRICT -> {
                                throw IllegalStateException(message)
                            }
                        }
                    }
                }
            }
        }
    }
}