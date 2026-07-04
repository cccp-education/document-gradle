package document

import java.io.File

/**
 * Single OCR-ed AsciiDoc page produced by codex-gradle (Brooklyn).
 *
 * A [BookPage] is one page of an existing book, transcribed by Codex
 * `collectOcr` into a structured AsciiDoc file on disk (the N2 <-> N2
 * bridge between Brooklyn and New Orleans).
 *
 * Pages are ordered by [order] (PageOrder), derived from the leading
 * digits of the file name (e.g. `001-page.adoc`, `002-page.adoc`).
 * The original photo is optional — it may be referenced for illustration
 * in the final book via an AsciiDoc `image::` directive.
 */
data class BookPage(
    val file: File,
    val order: PageOrder,
    val photo: File? = null,
) {

    init {
        require(file.extension.equals("adoc", ignoreCase = true)) {
            "BookPage must be an .adoc file, got: ${file.name}"
        }
    }

    val name: String get() = file.nameWithoutExtension

    fun readText(): String = file.readText()

    companion object {

        /**
         * Builds a [BookPage] from a single AsciiDoc page file, deriving
         * the [PageOrder] from the leading digits of the file name.
         *
         * Files without a numeric prefix are ordered after numbered ones,
         * preserving alphabetic order among them.
         */
        fun fromFile(file: File, photosDir: File? = null): BookPage {
            val order = PageOrder.fromFileName(file.nameWithoutExtension)
            val photo = photosDir?.let { dir ->
                val baseName = file.nameWithoutExtension
                listOf("png", "jpg", "jpeg", "webp").forEach { ext ->
                    val candidate = File(dir, "$baseName.$ext")
                    if (candidate.exists()) return@let candidate
                }
                null
            }
            return BookPage(file, order, photo)
        }
    }
}