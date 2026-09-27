package document

import java.io.File

/**
 * Materialises the page scans an assembled book actually references (EPIC
 * DOC-BOOK-IMAGES, extracted as a shared helper in DOC-BOOK-TRANSLATE US-2).
 *
 * AsciidoctorJ only *embeds* an image located inside the assembled book's own
 * directory: an absolute path is silently dropped (epubcheck RSC-007) and a
 * relative path escaping the container is rejected (RSC-026). The assembled
 * book therefore emits a flat `image::NNN.jpg[]` with a relative
 * `:imagesdir: images`, and the referenced scans are copied next to the book.
 *
 * Ink Economy Law: only the scans the book *references* are copied — never the
 * whole scanned corpus (hundreds of megabytes).
 */
object BookImageMaterializer {

    /** Relative directory the referenced scans are materialised into (S-260). */
    const val IMAGES_DIR = "images"

    /**
     * The scans [bookContent] references, keyed by the base name they must be
     * materialised under next to the book. Derived from the content itself:
     * every `image::` / `image:` target that resolves to a real file in
     * [imageDir] is a scan to copy (illustration, repaired ghost or
     * pre-existing image alike).
     */
    fun resolveTargets(bookContent: String, imageDir: File): Map<String, File> {
        val scans = LinkedHashMap<String, File>()
        BookImageRewriter.targets(bookContent).forEach { target ->
            val source = imageDir.resolve(target)
            if (source.isFile) scans[target] = source
        }
        return scans
    }

    /**
     * Copies the referenced [scans] into a flat `images/` directory below
     * [outputDir] so the relative `image::` targets resolve and the EPUB embeds
     * them (RSC-007-free).
     */
    fun materialise(scans: Map<String, File>, outputDir: File) {
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
}
