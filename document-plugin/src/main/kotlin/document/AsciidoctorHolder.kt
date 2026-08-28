package document

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
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
}
