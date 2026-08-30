package document.security

/**
 * Strictness mode for the include-path guard applied before conversion
 * (DOC-CR4). Mirrors the `STRICT`/`LENIENT`/`OFF` convention used by
 * `document.translation.validation.ValidationMode` but lives in the `security`
 * package to stay decoupled from the translation domain.
 *
 * - [STRICT]  : a forbidden include (path traversal / absolute) fails the build
 * - [LENIENT] : a forbidden include is logged as a warning, conversion continues
 * - [OFF]     : no audit (default — backward-compatible with pre-CR4 builds)
 */
enum class IncludeGuardMode {
    STRICT,
    LENIENT,
    OFF,
}
