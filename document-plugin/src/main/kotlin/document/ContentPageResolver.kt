package document

import java.io.File

/**
 * Resolves the OCR-ed AsciiDoc content of a [BookSection] from a scanned-page
 * directory.
 *
 * A scanned content corpus stores each physical page as
 * `<page zero-padded to 3>.adoc` (e.g. page 14 -> `014.adoc`) with optional
 * continuation splits `<page>_N.adoc` / `<page padded>_N.adoc` (e.g.
 * `055_2.adoc`) when a single book page was split across several scans. This
 * is the content-specific counterpart of [BookAssembler.pageContentResolver],
 * which assumes the `%03d-*.adoc` codex naming convention and therefore
 * cannot read a scanned-page corpus.
 *
 * EPIC DOC-BOOK-IMAGES (fixes code-review S-258 B4/B5) extends this resolver
 * with the page scans:
 * - a *ghost* `image::` directive (caption recognised by OCR, target file
 *   absent) is always repaired via [BookImageRewriter] — substituted with the
 *   page scan when one exists, dropped otherwise — so the assembled book stays
 *   epubcheck-valid (RSC-007);
 * - when [photosDir] is configured, each physical page scan is emitted as an
 *   `image::` illustration (opt-in: it is not desirable to embed the whole
 *   scanned corpus by default — Ink Economy Law).
 *
 * Ink Economy Law: pure file read, deterministic, no re-OCR, no mutation of any
 * source scan.
 */
class ContentPageResolver(
    private val scansDir: File,
    private val photosDir: File? = null,
) {

    private val imageDir: File get() = photosDir ?: scansDir

    fun content(section: BookSection): String {
        val padded = section.page.toString().padStart(3, '0')
        val unpadded = section.page.toString()
        val files = mutableListOf<File>()
        val primary = scansDir.resolve("$padded.adoc")
        if (primary.isFile) files += primary
        scansDir
            .listFiles { f ->
                f.isFile &&
                    f.extension.equals("adoc", ignoreCase = true) &&
                    f.name.matches(Regex("^($padded|$unpadded)_\\d+\\.adoc$"))
            }
            ?.sortedBy { it.name }
            ?.let { files += it }
        if (files.isEmpty()) return ""
        return files.joinToString("\n\n") { render(it) }.trim()
    }

    private fun render(file: File): String =
        BookPageRenderer.render(file, imageDir, illustrate = photosDir != null)
}
