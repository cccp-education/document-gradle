package document

import contracts.i18n.TranslationService
import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import document.translation.PooledOllamaTranslationAdapter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

/**
 * Translates a scanned structured book into **several** target languages
 * (EPIC DOC-BOOK-MULTILANG, US-2).
 *
 * The plan is derived from the N0 `LanguageCatalog` by the pure
 * [BookLanguagePlanner] (decision D2/D3): an explicit `targetLanguages` subset
 * wins, otherwise `translateToAll` expands to the whole catalog minus the source
 * language. For each [BookLanguageTarget] the task delegates to the shared
 * [BookTranslationRunner] (decision D5), producing `book-<lang>.adoc` and
 * leaving `book.adoc` untouched — exactly as the mono-language `translateBook`
 * does for a single language.
 *
 * Ink Economy Law: opt-in (no knob → no work), and `skipExisting` skips a
 * language whose book already exists. The task is non-incrementable: the LLM is
 * not deterministic ([DisableCachingByDefault]).
 *
 * The task only *translates*; publishing the N books to N formats is a
 * distinct fan-out concern (decision D1) left to a future publication EPIC.
 */
@DisableCachingByDefault(because = "Translation output depends on LLM which is non-deterministic")
abstract class TranslateBookAllLanguagesTask : DefaultTask() {

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

    /**
     * DOC-BOOK-MULTILANG — when `true` (and [targetLanguages] is empty) the plan
     * expands to the whole `LanguageCatalog` minus [sourceLanguage].
     */
    @get:Input
    abstract val translateToAll: Property<Boolean>

    /**
     * DOC-BOOK-MULTILANG — an explicit target-language subset (takes precedence
     * over [translateToAll]). Empty by default.
     */
    @get:Input
    abstract val targetLanguages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val llmMode: Property<String>

    /** DOC-BOOK-MULTILANG — do not overwrite an already produced translation. */
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

    /**
     * DOC-BOOK-MULTILANG — the base name of the produced books (default `book`),
     * so each target writes `<baseName>-<lang>.adoc` next to the assembled book.
     */
    @get:Input
    abstract val outputFileName: Property<String>

    /**
     * DOC-BOOK-MULTILANG — the directory the translated books are written into
     * (same directory as the assembled `book.adoc`, so the naming stays
     * coherent). Not a declared output on its own (the parent directory is
     * shared with other tasks); the produced files are declared through
     * [outputFiles].
     */
    @get:Internal
    abstract val outputDir: DirectoryProperty

    /** DOC-BOOK-MULTILANG — the resolved translated books (one per language). */
    @get:OutputFiles
    abstract val outputFiles: ConfigurableFileCollection

    init {
        group = "document"
        description = "Translates a structured book into several target languages (domain-level, structure regenerated). — DOC-BOOK-MULTILANG"
        sourceLanguage.convention("fr")
        translateToAll.convention(false)
        targetLanguages.convention(emptyList())
        llmMode.convention("ollama")
        skipExisting.convention(false)
        matterPolicyMode.convention(MatterPolicyMode.DERIVED)
        matterBreaks.convention(false)
        navigation.convention(false)
        outputFileName.convention("book")
    }

    @TaskAction
    fun translateBookAllLanguages() {
        val logger = LoggerFactory.getLogger(TranslateBookAllLanguagesTask::class.java)
        val source = sourceLanguage.getOrElse("fr")
        val baseName = outputFileName.getOrElse("book")

        val plan = BookLanguagePlanner.plan(
            sourceLanguage = source,
            requested = targetLanguages.getOrElse(emptyList()),
            translateToAll = translateToAll.getOrElse(false),
        )

        // Ink Economy Law — no language selected is a strict no-op: the book
        // pipeline is left untouched (backward compatible).
        if (plan.isEmpty()) {
            logger.info("{} — no target language configured, skipping translation", name)
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

        val photos = photosDir.orNull?.asFile
        val tree = BookTreeBuilder.fromSections(sections)
        val resolveContent = BookAssembler.contentAwareResolver(pages, photos)

        val serviceFactory = translationServiceFactory(llmMode.getOrElse("ollama"))
        val destination = outputDir.orNull?.asFile ?: pages
        val matterPolicy = MatterPolicy.forMode(matterPolicyMode.get(), sections)

        var written = 0
        plan.forEach { target ->
            val output = destination.resolve(target.fileName(baseName))
            if (skipExisting.getOrElse(false) && output.isFile) {
                logger.info("{} — '{}' already exists, skipping (Ink Economy Law)", name, output.name)
                return@forEach
            }

            val service = serviceFactory(target.code)
            val runner = BookTranslationRunner(
                BookTranslator(service, documentTranslator = DocumentTranslator(service)),
            )
            runner.translateAndPublish(
                BookTranslationRequest(
                    tree = tree,
                    resolveContent = resolveContent,
                    pagesDir = pages,
                    photosDir = photos,
                    title = title.get(),
                    author = author.get(),
                    sourceLanguage = source,
                    targetLanguage = target.code,
                    matterPolicy = matterPolicy,
                    matterBreaks = matterBreaks.get(),
                    navigation = navigation.get(),
                    outputFile = output,
                ),
            )
            written++
        }

        logger.info(
            "{} — translated book into {}/{} languages (source: {})",
            name,
            written,
            plan.size,
            source,
        )
    }

    /**
     * A per-target-language [TranslationService] factory. The deterministic
     * `fake` mode suffixes every translation with the **target** language code
     * (so each produced book is distinguishable in tests); the `ollama` mode
     * shares one pooled adapter across languages.
     */
    private fun translationServiceFactory(mode: String): (String) -> TranslationService {
        val fake: (String) -> TranslationService = { target -> FakeTranslationService(" [${target.uppercase()}]") }
        return when (mode.lowercase()) {
            "fake" -> fake
            "ollama" -> {
                val pooled = PooledOllamaTranslationAdapter.create()
                val shared: (String) -> TranslationService = { _ -> pooled }
                shared
            }
            else -> throw IllegalArgumentException("Unknown llmMode: '$mode' — expected 'ollama' or 'fake'")
        }
    }
}
