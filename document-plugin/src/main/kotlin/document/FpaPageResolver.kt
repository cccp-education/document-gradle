package document

import java.io.File

/**
 * Resolves the OCR-ed AsciiDoc content of a [BookSection] from the FPA scan
 * directory.
 *
 * The FPA corpus stores each physical page as `<page zero-padded to 3>.adoc`
 * (e.g. page 14 -> `014.adoc`) with optional continuation splits
 * `<page>_N.adoc` / `<page padded>_N.adoc` (e.g. `055_2.adoc`) when a single
 * book page was split across several scans. This is the FPA-specific counterpart
 * of [BookAssembler.pageContentResolver], which assumes the `%03d-*.adoc` codex
 * naming convention and therefore cannot read the existing FPA corpus.
 *
 * Ink Economy Law: pure file read, deterministic, no re-OCR, no mutation of any
 * source scan.
 */
class FpaPageResolver(private val scansDir: File) {

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
        return files.joinToString("\n\n") { it.readText().trim() }.trim()
    }
}
