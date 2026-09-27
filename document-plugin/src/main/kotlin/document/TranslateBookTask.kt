package document

import contracts.i18n.TranslationService
import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import document.translation.PooledOllamaTranslationAdapter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

/**
 * Translates a scanned structured book into a target language (EPIC
 * DOC-BOOK-TRANSLATE, US-2).
 *
 * The task mirrors [AssembleBookTask] but inserts the [BookTranslator] between
 * the TOC (structured tree) and the assembly: the book *domain* is translated
 * (node titles + page bodies), then [BookAssembler] regenerates the structural
 * AsciiDoc — anchors `[[ref]]`, `:doctype: book`, `toc::[]`, navigation — so
 * the structure is preserved **by construction** (decision D1). It never
 * translates the assembled document: the pivot parser is lossy on book
 * constructions (constat #2).
 *
 * Produces `book-<lang>.adoc` (the caller derives the file name from the
 * `outputFileName` knob), leaving `book.adoc` untouched.
 *
 * Ink Economy Law: opt-in (a blank target language leaves the task inactive)
 * and `skipExisting` avoids re-translating an already produced book. The task
 * is non-incrementable: the LLM is not deterministic
 * ([DisableCachingByDefault]).
 */
@DisableCachingByDefault(because = "Translation output depends on LLM which is non-deterministic")
abstract class TranslateBookTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val pagesDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val photosDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val tocFile: RegularFileProperty

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val author: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val targetLanguage: Property<String>

    @get:Input
    @get:Optional
    abstract val llmMode: Property<String>

    /** DOC-BOOK-TRANSLATE — do not overwrite an already produced translation. */
    @get:Input
    @get:Optional
    abstract val skipExisting: Property<Boolean>

    /** DOC-BOOK-MATTER — how the matter policy is resolved from the TOC. */
    @get:Input
    abstract val matterPolicyMode: Property<MatterPolicyMode>

    /** DOC-BOOK-MATTER — emit hard page breaks at matter transitions (opt-in). */
    @get:Input
    abstract val matterBreaks: Property<Boolean>

    /** DOC-BOOK-CONSISTENCY-B6 — emit previous / next cross-references (opt-in). */
    @get:Input
    abstract val navigation: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        description = "Translates a scanned structured book into a target language (domain-level, structure regenerated). — DOC-BOOK-TRANSLATE"
        sourceLanguage.convention("fr")
        targetLanguage.convention("")
        llmMode.convention("ollama")
        skipExisting.convention(false)
        matterPolicyMode.convention(MatterPolicyMode.DERIVED)
        matterBreaks.convention(false)
        navigation.convention(false)
    }

    @TaskAction
    fun translateBook() {
        val logger = LoggerFactory.getLogger(TranslateBookTask::class.java)
        val target = targetLanguage.getOrElse("")
        val source = sourceLanguage.getOrElse("fr")
        val output = outputFile.get().asFile

        // Ink Economy Law — a blank or identical target language is a strict
        // no-op: the book pipeline is left untouched (backward compatible).
        if (target.isBlank() || target == source) {
            logger.info("{} — no target language configured, skipping translation", name)
            return
        }
        if (skipExisting.getOrElse(false) && output.isFile) {
            logger.info("{} — '{}' already exists, skipping (Ink Economy Law)", name, output.absolutePath)
            return
        }

        val pages = pagesDir.orNull?.asFile
        if (pages == null || !pages.isDirectory) {
            logger.warn("{} — pages directory missing, skipping translation", name)
            return
        }

        val toc = tocFile.orNull?.asFile
        if (toc == null || !toc.exists()) {
            logger.warn("{} — tocFile missing, skipping translation", name)
            return
        }
        val sections = BookTocParser.parse(toc)
        if (sections.isEmpty()) {
            logger.warn("{} — TOC '{}' carries no section, skipping translation", name, toc.absolutePath)
            return
        }

        val translationService = resolveTranslationService(llmMode.getOrElse("ollama"), target)

        val runner = BookTranslationRunner(
            BookTranslator(translationService, documentTranslator = DocumentTranslator(translationService)),
        )
        runner.translateAndPublish(
            BookTranslationRequest(
                tree = BookTreeBuilder.fromSections(sections),
                resolveContent = BookAssembler.contentAwareResolver(pages, photosDir.orNull?.asFile),
                pagesDir = pages,
                photosDir = photosDir.orNull?.asFile,
                title = title.get(),
                author = author.get(),
                sourceLanguage = source,
                targetLanguage = target,
                matterPolicy = MatterPolicy.forMode(matterPolicyMode.get(), sections),
                matterBreaks = matterBreaks.get(),
                navigation = navigation.get(),
                outputFile = output,
            ),
        )

        logger.info(
            "{} — translated book ({} → {}) -> {} ({} bytes)",
            name,
            source,
            target,
            output.absolutePath,
            output.length(),
        )
    }

    private fun resolveTranslationService(mode: String, target: String): TranslationService =
        when (mode.lowercase()) {
            "fake" -> FakeTranslationService(" [${target.uppercase()}]")
            "ollama" -> PooledOllamaTranslationAdapter.create()
            else -> throw IllegalArgumentException("Unknown llmMode: '${mode}' — expected 'ollama' or 'fake'")
        }
}
