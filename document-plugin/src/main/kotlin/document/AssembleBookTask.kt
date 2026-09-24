package document

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import java.io.File
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
 * Assembles OCR-ed AsciiDoc pages (produced by codex-gradle) into a single
 * book document.
 *
 * DOC-11 — Book pipeline Codex -> Document:
 * - Reads all `.adoc` pages from [pagesDir] (the N2 <-> N2 bridge)
 * - Orders them by numeric prefix (PageOrder)
 * - Merges their content under a single AsciiDoc header
 * - Embeds original photos from [photosDir] as `image::` directives
 * - Writes the assembled book to [outputFile]
 *
 * The source page files are never mutated (Rule 7).
 *
 * DOC-BOOK-VALIDATE-2 — optional post-assembly validation against the book
 * TOC ([tocFile]) and original PDFs ([pdfsDir]) via [BookValidator] and
 * [BookTocParser]. [validationMode] controls how findings are surfaced:
 * [ValidationMode.LENIENT] logs warnings and never fails the build (default),
 * [ValidationMode.STRICT] fails the build with a [GradleException].
 */
@DisableCachingByDefault(because = "Idempotence is applicative — deterministic assembly from ordered pages")
abstract class AssembleBookTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val pagesDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val photosDir: DirectoryProperty

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val author: Property<String>

    @get:Input
    @get:Optional
    abstract val outputFileName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val tocFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val pdfsDir: DirectoryProperty

    @get:Input
    abstract val validationMode: Property<ValidationMode>

    /**
     * DOC-BOOK-MATTER — how the matter policy is resolved from the TOC.
     * [MatterPolicyMode.DERIVED] (default) adopts the convention roots only
     * when the TOC declares them, removing the permanent S3 false positive on
     * a body-only book; [MatterPolicyMode.LEGACY] keeps the historical `0`/`9`
     * requirement.
     */
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
        validationMode.convention(ValidationMode.LENIENT)
        matterPolicyMode.convention(MatterPolicyMode.DERIVED)
        matterBreaks.convention(false)
        navigation.convention(false)
    }

    @TaskAction
    fun assemble() {
        val logger = LoggerFactory.getLogger(AssembleBookTask::class.java)
        val pages = pagesDir.orNull?.asFile
        val photos = photosDir.orNull?.asFile
        val output = outputFile.get().asFile

        if (pages == null || !pages.isDirectory) {
            logger.warn("{} — pages directory missing, skipping assembly", name)
            return
        }

        val toc = tocFile.orNull?.asFile
        val tocPresent = toc != null && toc.exists()
        val sections = if (tocPresent) BookTocParser.parse(toc!!) else emptyList()

        // DOC-BOOK-MATTER — resolve the matter policy against the real TOC.
        // DERIVED (default) adopts the convention roots only when the TOC
        // actually declares them, so a body-only book is not permanently
        // flagged; LEGACY keeps the historical 0/9 requirement.
        val matterPolicy = MatterPolicy.forMode(matterPolicyMode.get(), sections)

        val result = if (tocPresent && sections.isNotEmpty()) {
            // DOC-BOOK-DOMAIN-3 — structured, navigable assembly from the TOC tree.
            // EPIC DOC-BOOK-IMAGES — photosDir enables the page-scan illustration
            // and the ghost-image repair (S-258 B4/B5). The referenced scans are
            // materialised in `images/` next to the assembled book and the header
            // emits a *relative* `:imagesdir: images` — AsciidoctorJ only embeds
            // (and epubcheck only accepts) images inside the source directory;
            // an absolute path is silently dropped (RSC-007) and a path escaping
            // the container is rejected (RSC-026). Empirical proof: S-260.
            val tree = BookTreeBuilder.fromSections(sections)
            val resolveContent = BookAssembler.contentAwareResolver(pages, photos)
            val baseLayout = BookLayout(
                emitMatterBreaks = matterBreaks.get(),
                matterPolicy = matterPolicy,
                emitNavigation = navigation.get(),
            )
            val assembled = BookAssembler.assemble(tree, baseLayout, title.get(), author.get(), resolveContent)
            // The targets the assembled book actually references are the exact
            // (and only) scans to materialise — illustration, repaired ghosts and
            // pre-existing images alike (Ink Economy Law: never copy an
            // unreferenced scan, never risk a dangling target).
            val referenced = resolveTargets(assembled.content, photos ?: pages)
            if (referenced.isEmpty()) {
                assembled
            } else {
                materialiseScans(referenced, output.parentFile)
                BookAssembler.assemble(
                    tree = tree,
                    layout = BookLayout(
                        imagesDir = IMAGES_DIR,
                        emitMatterBreaks = matterBreaks.get(),
                        matterPolicy = matterPolicy,
                        emitNavigation = navigation.get(),
                    ),
                    title = title.get(),
                    author = author.get(),
                    resolveContent = resolveContent,
                )
            }
        } else {
            BookAssembler.assemble(pages, title.get(), author.get(), photos)
        }

        result.writeTo(output)
        logger.info(
            "{} — assembled book -> {} ({} bytes, structured={})",
            name,
            output.absolutePath,
            output.length(),
            tocPresent && sections.isNotEmpty(),
        )

        // BOOK-6 — locate OCR / LLM-vision failures for human iteration: the
        // report carries page number + owning TOC section ref + title.
        val issues = if (tocPresent) BookOcrFailureDetector.detect(pages, sections) else emptyList()
        if (issues.isNotEmpty()) {
            val reportFile = output.parentFile.resolve("book-ocr-issues.json")
            BookOcrIssueReport.write(issues, reportFile)
            logger.warn(
                "{} — {} OCR failure(s) located for human iteration, see {}",
                name,
                issues.size,
                reportFile.absolutePath,
            )
        }

        validateIfConfigured(logger, pages, toc, sections, matterPolicy)
    }

    /**
     * EPIC DOC-BOOK-IMAGES — the scans the assembled book references, keyed by
     * the base name they must be materialised under next to the book.
     *
     * Derived from the assembled content itself: every `image::` target that
     * resolves to a real file in [imageDir] is a scan to copy (the illustration
     * emits it, the ghost repair substitutes it, a pre-existing image already
     * points at one). Only the referenced scans are returned — the whole scanned
     * corpus (hundreds of megabytes) is never copied (Ink Economy Law).
     */
    private fun resolveTargets(bookContent: String, imageDir: File): Map<String, File> {
        val scans = LinkedHashMap<String, File>()
        BookImageRewriter.targets(bookContent).forEach { target ->
            val source = imageDir.resolve(target)
            if (source.isFile) scans[target] = source
        }
        return scans
    }

    /**
     * Copies the referenced [scans] into a flat `images/` directory next to the
     * assembled book, so the relative `image::` targets resolve and the EPUB
     * embeds them (RSC-007-free). The scanned corpus stores scans in a flat
     * directory, so a flat target keeps the emitted names stable.
     */
    private fun materialiseScans(scans: Map<String, File>, outputDir: File) {
        if (scans.isEmpty()) return
        val target = outputDir.resolve(IMAGES_DIR).apply { mkdirs() }
        scans.forEach { (name, source) ->
            if (!source.isFile) return@forEach
            val destination = target.resolve(name)
            if (source.absoluteFile != destination.absoluteFile) {
                source.copyTo(destination, overwrite = true)
            }
        }
    }

    private fun validateIfConfigured(
        logger: org.slf4j.Logger,
        pages: File,
        toc: File?,
        sections: List<BookSection>,
        matterPolicy: MatterPolicy,
    ) {
        val tocFile = toc ?: tocFile.orNull?.asFile ?: return
        if (!tocFile.exists()) {
            logger.warn("{} — tocFile '{}' not found, skipping validation", name, tocFile.absolutePath)
            return
        }

        val pdfs = pdfsDir.orNull?.asFile

        // DOC-BOOK-DOMAIN-6 — tree-level structural validation (ref continuity,
        // uniqueness, matter completeness, page order monotonicity), merged with
        // the file-level validation (DOC-BOOK-VALIDATE) into a single report.
        val structural = BookValidator.validateStructure(sections, matterPolicy = matterPolicy)
        val fileBased = BookValidator.validate(pagesDir = pages, toc = sections, pdfsDir = pdfs)
        val reasons = buildList {
            if (structural is BookValidationResult.Invalid) {
                addAll(structural.reasons.map { "structure — $it" })
            }
            if (fileBased is BookValidationResult.Invalid) addAll(fileBased.reasons)
        }

        if (reasons.isEmpty()) {
            logger.info(
                "{} — book validation OK ({} pages covered by TOC, structure coherent)",
                name,
                (fileBased as? BookValidationResult.Valid)?.pageCount ?: sections.size,
            )
        } else {
            when (validationMode.get()) {
                ValidationMode.LENIENT -> reasons.forEach {
                    logger.warn("{} — book validation (lenient): {}", name, it)
                }
                ValidationMode.STRICT -> BookValidator.enforce(ValidationMode.STRICT, reasons)
            }
        }
    }

    private companion object {
        /** Relative directory the referenced scans are materialised into (S-260). */
        const val IMAGES_DIR = "images"
    }
}