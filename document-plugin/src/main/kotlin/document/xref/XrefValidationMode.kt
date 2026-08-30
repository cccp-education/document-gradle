package document.xref

/**
 * Strictness mode applied to cross-reference validation of an AsciiDoc document.
 *
 * Parallel to [document.security.IncludeGuardMode] : the validator is a pure
 * function of the document text and the mode only changes how findings are
 * surfaced (silent / warn / fail-fast).
 *
 * - [OFF]     : validation disabled (default — backward-compatible).
 * - [LENIENT] : unresolved references are logged as warnings, the build still converts.
 * - [STRICT]  : any unresolved reference fails the build with a [org.gradle.api.GradleException].
 */
enum class XrefValidationMode {
    OFF,
    LENIENT,
    STRICT,
}
