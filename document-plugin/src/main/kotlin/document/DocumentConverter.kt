package document

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import java.io.File
import java.security.MessageDigest

/**
 * Convertisseur de documents AsciiDoc vers les formats supportes par AsciidoctorJ.
 *
 * Ubiquitous language : un [DocumentConverter] transforme un [DocumentSource]
 * en livrable multi-format via AsciidoctorJ. Le convertisseur lit la source
 * (lecture seule — Regle 7) et produit le contenu du fichier de sortie.
 *
 * DOC-3 : conversion HTML5 (backend html5).
 * DOC-4 : conversion PDF (backend pdf).
 * DOC-5 : conversions EPUB3, DocBook 5, ManPage.
 *
 * Loi de l'Economie d'Encre : avant toute conversion, verifier qu'un fichier
 * de sortie existe et que le hash de la source correspond au hash stocke en
 * metadata du fichier genere. Ne pas re-convertir un resultat valide.
 */
object DocumentConverter {

    private const val METADATA_MARKER = "<!-- document-gradle — source hash:"

    /**
     * Convertit un [DocumentSource] en HTML5 (backend html5 d'AsciidoctorJ).
     *
     * @param source le fichier AsciiDoc source (lecture seule)
     * @return le contenu HTML5 produit
     */
    fun convertToHtml(source: DocumentSource): String {
        return convert(source, "html5")
    }

    /**
     * Convertit un [DocumentSource] en PDF (backend pdf d'AsciidoctorJ) et ecrit
     * le resultat binaire dans [output].
     *
     * DOC-4 : AsciidoctorJ PDF ecrit directement dans un fichier (pas de retour String).
     *
     * @param source le fichier AsciiDoc source (lecture seule)
     * @param output le fichier PDF de sortie a ecrire
     */
    fun convertToPdf(source: DocumentSource, output: java.io.File) {
        convertToFile(source, "pdf", output)
    }

    /**
     * Convertit un [DocumentSource] vers le backend AsciidoctorJ demande.
     *
     * @param source le fichier AsciiDoc source (lecture seule)
     * @param backend le backend AsciidoctorJ ("html5", "pdf", "epub3", "docbook5", "manpage")
     * @return le contenu du fichier de sortie
     */
    fun convert(source: DocumentSource, backend: String): String {
        Asciidoctor.Factory.create().use { asciidoctor ->
            val options = Options.builder()
                .backend(backend)
                .safe(SafeMode.UNSAFE)
                .standalone(true)
                .option("sourcemap", "true")
                .toFile(false)
                .build()
            return asciidoctor.convertFile(source.file, options)
                ?: throw IllegalStateException("AsciidoctorJ a produit une sortie nulle pour ${source.file.name} (backend=$backend)")
        }
    }

    /**
     * Convertit un [DocumentSource] vers un backend binaire (pdf, epub3) et ecrit
     * le resultat directement dans [output].
     *
     * AsciidoctorJ ecrit les formats binaires dans un fichier (pas de retour String).
     *
     * @param source le fichier AsciiDoc source (lecture seule)
     * @param backend le backend AsciidoctorJ ("pdf", "epub3")
     * @param output le fichier de sortie a ecrire
     */
    fun convertToFile(source: DocumentSource, backend: String, output: java.io.File) {
        output.parentFile.mkdirs()
        Asciidoctor.Factory.create().use { asciidoctor ->
            val options = Options.builder()
                .backend(backend)
                .safe(SafeMode.UNSAFE)
                .toFile(output)
                .build()
            asciidoctor.convertFile(source.file, options)
        }
    }

    /**
     * Decide si la conversion doit etre skippee (economie d'encre).
     *
     * @param source le fichier AsciiDoc source
     * @param output le fichier de sortie existant (ou absent)
     * @return true si le fichier existe et que le hash source correspond au hash stocke en metadata
     */
    fun shouldSkipConversion(source: DocumentSource, output: File): Boolean {
        if (!output.exists()) return false
        val expectedHash = sourceHash(source)
        val firstLines = output.bufferedReader().useLines { sequence ->
            sequence.take(3).joinToString("\n")
        }
        return firstLines.contains("$METADATA_MARKER $expectedHash")
    }

    /**
     * Calcule le hash SHA-256 du contenu de la source AsciiDoc.
     * Sert de signature pour l'invalidation (economie d'encre).
     */
    fun sourceHash(source: DocumentSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(source.readText().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Construit l'en-tete de metadata insere au debut du fichier de sortie.
     * Permet l'invalidation par hash de source (economie d'encre).
     */
    fun buildMetadataHeader(source: DocumentSource): String =
        "$METADATA_MARKER ${sourceHash(source)} -->\n"

    /**
     * Decide si la conversion d'un format binaire (PDF, EPUB) doit etre skippee.
     *
     * Pour les formats binaires, le hash ne peut pas etre insere dans le fichier
     * lui-meme. Un fichier sidecar `<output>.sourcehash` stocke le hash de la source.
     *
     * @param source le fichier AsciiDoc source
     * @param output le fichier de sortie binaire existant (ou absent)
     * @return true si le fichier existe, le sidecar existe et le hash correspond
     */
    fun shouldSkipBinaryConversion(source: DocumentSource, output: java.io.File): Boolean {
        if (!output.exists()) return false
        val sidecar = java.io.File(output.parentFile, output.name + BINARY_HASH_SUFFIX)
        if (!sidecar.exists()) return false
        val expectedHash = sourceHash(source)
        return sidecar.readText().trim() == expectedHash
    }

    /**
     * Ecrit le hash de la source dans un fichier sidecar `<output>.sourcehash`.
     * Permet l'invalidation par hash de source pour les formats binaires (economie d'encre).
     *
     * @param source le fichier AsciiDoc source
     * @param output le fichier de sortie binaire
     */
    fun writeBinaryMetadataHeader(source: DocumentSource, output: java.io.File) {
        val sidecar = java.io.File(output.parentFile, output.name + BINARY_HASH_SUFFIX)
        sidecar.writeText(sourceHash(source))
    }

    private const val BINARY_HASH_SUFFIX = ".sourcehash"
}