package document

import java.io.File

/**
 * Configuration du pipeline documentaire.
 *
 * Ubiquitous language : un [DocumentConfig] decrit comment transformer
 * un [DocumentSource] en livrables multi-format via AsciidoctorJ.
 *
 * - [source] : le fichier AsciiDoc en entree (lecture seule)
 * - [outputDir] : le repertoire de sortie des fichiers generes
 * - [formats] : les formats de publication demandes (HTML, PDF, EPUB, ...)
 * - [pdfTheme] : fichier YML de theme Asciidoctor PDF (optionnel, DOC-10)
 * - [htmlStylesheet] : feuille de style CSS pour la sortie HTML (optionnel, DOC-10)
 * - [enrichPlantUml] : activer l'incrustation des diagrammes PlantUML
 * - [enrichImages] : activer l'incrustation des images
 * - [enrichPassthrough] : preserver les blocs HTML passthrough
 * - [theme] : theme visuel applique lors de la conversion (DOC-10)
 */
data class DocumentConfig(
    val source: DocumentSource,
    val outputDir: File,
    val formats: List<DocumentFormat> = listOf(DocumentFormat.HTML),
    val pdfTheme: File? = null,
    val htmlStylesheet: File? = null,
    val enrichPlantUml: Boolean = false,
    val enrichImages: Boolean = false,
    val enrichPassthrough: Boolean = false,
    val theme: DocumentTheme = DocumentTheme(),
) {

    fun outputFor(format: DocumentFormat): File =
        File(outputDir, "${source.name}.${format.extension}")

    fun requiresEnrichment(): Boolean =
        enrichPlantUml || enrichImages || enrichPassthrough
}