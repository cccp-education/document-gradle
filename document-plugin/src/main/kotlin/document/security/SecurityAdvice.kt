package document.security

/**
 * Outcome of auditing the coherence of the conversion security configuration
 * (combination of AsciidoctorJ [org.asciidoctor.SafeMode] and [IncludeGuardMode]).
 *
 * Ubiquitous language (DOC-CR5) :
 * - [Valid]   : the two guards are coherent (at least the filesystem is restricted,
 *   or no guard is active — backward-compatible default).
 * - [Warn]    : asymmetric posture — an include guard is active but AsciidoctorJ
 *   SafeMode is UNSAFE (non-include filesystem access stays unrestricted). Degraded.
 * - [Reject]  : same asymmetry under STRICT include guard — must fail the build fast.
 */
sealed interface SecurityAdvice {

    /** The security configuration is coherent. */
    data object Valid : SecurityAdvice

    /**
     * Asymmetric but tolerated (LENIENT include guard + UNSAFE SafeMode).
     * @param reason human-readable explanation of the detected asymmetry
     */
    data class Warn(val reason: String) : SecurityAdvice

    /**
     * Asymmetric and unacceptable (STRICT include guard + UNSAFE SafeMode).
     * @param reason human-readable explanation of the detected asymmetry
     */
    data class Reject(val reason: String) : SecurityAdvice
}
