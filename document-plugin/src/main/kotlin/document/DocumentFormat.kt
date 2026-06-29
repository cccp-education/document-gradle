package document

/**
 * Format de publication supporte par AsciidoctorJ.
 *
 * Ubiquitous language : un document AsciiDoc source est converti
 * vers un [DocumentFormat] de sortie via une tache Gradle isolee.
 */
enum class DocumentFormat(val backend: String, val extension: String) {
    HTML("html5", "html"),
    PDF("pdf", "pdf"),
    EPUB("epub3", "epub"),
    DOCBOOK("docbook5", "xml"),
    MANPAGE("manpage", "man");

    companion object {
        val ALL: List<DocumentFormat> = entries.toList()
    }
}