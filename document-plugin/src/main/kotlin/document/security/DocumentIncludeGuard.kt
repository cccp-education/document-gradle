package document.security

import org.gradle.api.GradleException
import java.io.File

/**
 * Applies the include-path guard [IncludeGuardMode] to an AsciiDoc document
 * before conversion (DOC-CR4-2).
 *
 * - [IncludeGuardMode.OFF]     : no audit (backward-compatible, default).
 * - [IncludeGuardMode.STRICT]  : a forbidden include throws [GradleException],
 *   failing the build before any AsciidoctorJ filesystem access.
 * - [IncludeGuardMode.LENIENT] : a forbidden include is reported via [onWarn]
 *   and conversion continues.
 *
 * Deterministic and side-effect free except for the optional warning callback.
 */
object DocumentIncludeGuard {

    /**
     * Audits [text] (the AsciiDoc source) resolved against [baseDir].
     *
     * @param text the document source content
     * @param baseDir directory relative includes resolve against (source parent)
     * @param mode the guard strictness
     * @param onWarn callback invoked in [IncludeGuardMode.LENIENT] on a finding
     * @throws GradleException in [IncludeGuardMode.STRICT] when a forbidden include is found
     */
    fun check(
        text: String,
        baseDir: File,
        mode: IncludeGuardMode,
        onWarn: (String) -> Unit = {},
    ) {
        if (mode == IncludeGuardMode.OFF) return
        val result = IncludePathValidator.validate(text, baseDir)
        if (result is IncludeValidationResult.Invalid) {
            val detail = "include guard detected '${result.offendingTarget}' at line ${result.line}: ${result.reason}"
            when (mode) {
                IncludeGuardMode.STRICT ->
                    throw GradleException("Include guard (STRICT) rejected conversion — $detail")
                IncludeGuardMode.LENIENT -> onWarn("Include guard (LENIENT) — $detail")
                IncludeGuardMode.OFF -> Unit
            }
        }
    }
}
