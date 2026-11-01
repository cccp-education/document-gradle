package document

import org.asciidoctor.SafeMode
import java.io.File

/**
 * Pure description of one AsciidoctorJ conversion (WARN-14).
 *
 * Ubiquitous language: an [AsciidoctorConversionRequest] states **what** to convert
 * (backend, output, attributes, safe-mode) without exposing the third-party
 * `org.asciidoctor.Options` type. [DocumentConverter.buildOptions] is the only
 * adapter turning it into AsciidoctorJ `Options`.
 *
 * Why this seam exists: AsciidoctorJ 3 marks `Options.map()` as deprecated
 * ("For internal use only"), so reading the built `Options` (as the SafeMode
 * tests used to) is no longer a stable contract. Asserting the request instead
 * keeps the tests on **our** contract — and is stronger, since backend,
 * standalone, sourcemap, output file and theme attributes become assertable.
 *
 * @property backend the AsciidoctorJ backend ("html5", "pdf", "epub3", "docbook5", "manpage")
 * @property safeMode the AsciidoctorJ safe-mode guard (UNSAFE/SERVER/SECURE)
 * @property standalone whether a standalone document is produced (always true today)
 * @property sourcemap whether a sourcemap is requested (always true today)
 * @property outputFile the output file, or null for an in-memory String conversion
 * @property attributes the AsciidoctorJ attributes injected from the theme (empty if none)
 */
internal data class AsciidoctorConversionRequest(
    val backend: String,
    val safeMode: SafeMode,
    val standalone: Boolean = true,
    val sourcemap: Boolean = true,
    val outputFile: File? = null,
    val attributes: Map<String, String> = emptyMap(),
)
