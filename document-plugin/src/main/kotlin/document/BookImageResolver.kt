package document

import java.io.File

/**
 * Resolves the original scan (photo) of a book page by its *page number*.
 *
 * [BookImageResolver] is the image counterpart of [ContentPageResolver]: where
 * the content resolver maps a [BookSection] to its OCR-ed AsciiDoc, this one
 * maps a page (or a physical source file base name) to the scan it was
 * photographed from.
 *
 * The scanned corpus stores scans next to the `.adoc` files with a *different*
 * padding convention than the content (`61.jpg` for page 61, not `061.jpg`;
 * `83_1.jpg` for a split). Both paddings, and the usual image extensions, are
 * probed so the same task serves every consumer without a convention flag.
 *
 * Ink Economy Law: pure file lookup, deterministic, no re-OCR, no mutation.
 */
object BookImageResolver {

    private val EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")

    /**
     * Resolves the scan of a physical source file from its base name
     * (file name without extension, e.g. `83_1`, `61`, `001-introduction`).
     *
     * The page is the leading digits of the name (both the scanned `NNN.adoc`
     * and the codex `%03d-title.adoc` conventions share it); an optional
     * `_<n>` split suffix right after the digits is honoured so a split page
     * points at its own split scan. The scanned corpus pads the content
     * (`055_2.adoc`) but not the scan (`55_2.jpg`), so both paddings are probed;
     * when the split has no scan, the fallback looks up the owning page scan
     * (`55.jpg`).
     *
     * @return the resolved scan file, or `null` when none exists
     */
    fun scanForBase(baseName: String, dir: File): File? {
        val match = PHYSICAL_KEY.find(baseName) ?: return exact(baseName, dir)
        val page = match.groupValues[1].toIntOrNull() ?: return exact(baseName, dir)
        val split = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val pageNames = listOf(page.toString(), page.toString().padStart(3, '0')).distinct()

        if (split != null) {
            pageNames.forEach { name -> exact("${name}_$split", dir)?.let { return it } }
        }
        pageNames.forEach { name -> exact(name, dir)?.let { return it } }
        return split(page, dir)
    }

    // Leading page digits, optionally followed by a `_<n>` split suffix.
    private val PHYSICAL_KEY = Regex("""^(\d+)(?:_(\d+))?""")

    /**
     * Resolves the scan of a page number, trying the unpadded and zero-padded
     * file names, then the split scans (`<page>_N`) in order.
     *
     * @return the resolved scan file, or `null` when none exists
     */
    fun scanFor(page: Int, dir: File): File? {
        exact(page.toString(), dir)?.let { return it }
        if (page < 100) exact(page.toString().padStart(3, '0'), dir)?.let { return it }
        return split(page, dir)
    }

    private fun exact(baseName: String, dir: File): File? {
        if (!dir.isDirectory) return null
        return EXTENSIONS.asSequence()
            .map { File(dir, "$baseName.$it") }
            .firstOrNull { it.isFile }
    }

    private fun split(page: Int, dir: File): File? {
        if (!dir.isDirectory) return null
        val pattern = Regex("""^(${page}|${page.toString().padStart(3, '0')})_\d+\.(png|jpg|jpeg|webp)$""", RegexOption.IGNORE_CASE)
        return dir.listFiles { f -> f.isFile && pattern.matches(f.name) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
    }
}
