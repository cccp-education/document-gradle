package document.security

import org.asciidoctor.SafeMode

/**
 * Pure DDD auditor of the conversion security configuration (DOC-CR5).
 *
 * The borough carries two independent filesystem guards :
 * - [org.asciidoctor.SafeMode] (AsciidoctorJ, DOC-CR3-2) restricts FS access globally.
 * - [IncludeGuardMode] (DOC-CR4) pre-flight audits `include::` directives only.
 *
 * An `include::` audit does NOT cover other AsciidoctorJ filesystem access (image
 * paths, data URIs, themes, `:docdir:`). Enabling the include guard while leaving
 * SafeMode at UNSAFE is therefore an *illusion of security*. This policy fails fast
 * (or warns) on that asymmetry, enforcing a coherent minimum posture.
 *
 * Ink Economy Law : resolution is deterministic and side-effect free — no file is
 * read, only the two enum values are classified.
 */
object DocumentSecurityPolicy {

    /**
     * Classifies the [safeMode] × [includeGuard] combination.
     *
     * @return [SecurityAdvice.Valid] when coherent, otherwise [SecurityAdvice.Warn]
     *   (LENIENT include guard + UNSAFE SafeMode) or [SecurityAdvice.Reject]
     *   (STRICT include guard + UNSAFE SafeMode).
     */
    fun advise(safeMode: SafeMode, includeGuard: IncludeGuardMode): SecurityAdvice {
        if (safeMode == SafeMode.UNSAFE && includeGuard != IncludeGuardMode.OFF) {
            val reason = "include guard $includeGuard is active but AsciidoctorJ SafeMode is UNSAFE — " +
                "non-include filesystem access (images, data URIs, themes, :docdir:) stays unrestricted; " +
                "set safeMode to at least SERVER"
            return if (includeGuard == IncludeGuardMode.STRICT) {
                SecurityAdvice.Reject(reason)
            } else {
                SecurityAdvice.Warn(reason)
            }
        }
        return SecurityAdvice.Valid
    }
}
