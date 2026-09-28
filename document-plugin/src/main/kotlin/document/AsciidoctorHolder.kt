package document

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.ast.Document
import java.io.File

/**
 * Partage une unique instance [Asciidoctor] sur l'ensemble des conversions
 * (DOC-CR3-1).
 *
 * La creation d'une instance [Asciidoctor] demarre l'runtime JRuby ; la recreer
 * a chaque conversion (5 formats du bookPipeline = 5 demarrages JRuby) est
 * couteux. L'instance est creee paresseusement lors du premier appel et
 * reutilisee pour toutes les conversions suivantes. L'acces est synchronise
 * car l'instance [Asciidoctor] n'est pas garantie thread-safe.
 *
 * Point de test : [setProviderForTest] injecte un provider compteur afin de
 * verifier la reutilisation (baby-step TDD, [AsciidoctorHolderTest]).
 */
internal object AsciidoctorHolder {

    @Volatile
    private var instance: Asciidoctor? = null

    @Volatile
    private var provider: () -> Asciidoctor = { Asciidoctor.Factory.create() }

    private val lock = Any()

    internal fun setProviderForTest(testProvider: () -> Asciidoctor) {
        provider = testProvider
        instance = null
    }

    internal fun resetProvider() {
        provider = { Asciidoctor.Factory.create() }
        instance = null
    }

    fun convertFile(source: File, options: Options): String? = synchronized(lock) {
        val asciidoctor = instance ?: provider().also { instance = it }
        asciidoctor.convertFile(source, options)
    }

    /**
     * Loads the AsciidoctorJ object model (AST) of [source] with the same shared
     * instance (DOC-SEMANTIC-TABLE, decision D6/D11).
     *
     * Parallel to [convertFile] : the JRuby runtime is started once and reused.
     * The caller passes the same [Options]/`SafeMode` as the conversion, so a
     * non-trusted document (OCR/LLM) cannot trigger filesystem access through
     * macros during the lifting.
     */
    fun load(source: File, options: Options): Document = synchronized(lock) {
        val asciidoctor = instance ?: provider().also { instance = it }
        asciidoctor.loadFile(source, options)
    }
}
