package document

import java.io.File

/**
 * Source AsciiDoc consommee par le pipeline documentaire.
 *
 * Ubiquitous language : le [DocumentSource] est le fichier .adoc en entree,
 * lu en lecture seule (Regle 7 — DOCUMENT CONSOMME ASCIIDOC).
 * La source n'est jamais modifiee ; seul le fichier genere est produit.
 */
data class DocumentSource(val file: File) {

    init {
        require(file.extension.equals("adoc", ignoreCase = true)) {
            "DocumentSource doit etre un fichier .adoc, recu: ${file.name}"
        }
    }

    val name: String get() = file.nameWithoutExtension

    fun readText(): String = file.readText()
}