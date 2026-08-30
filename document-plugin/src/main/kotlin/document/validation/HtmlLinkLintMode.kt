package document.validation

/**
 * Strictness mode applied to HTML link linting of a rendered document.
 *
 * Parallel to [document.xref.XrefValidationMode] and
 * [document.security.IncludeGuardMode] : the linter is a pure function of the
 * HTML text and the mode only changes how findings are surfaced (silent / warn /
 * fail-fast).
 *
 * - [OFF]     : linting disabled (default — backward-compatible).
 * - [LENIENT] : dead internal links are logged as warnings, the build still publishes.
 * - [STRICT]  : any dead internal link fails the build with a [org.gradle.api.GradleException].
 */
enum class HtmlLinkLintMode {
    OFF,
    LENIENT,
    STRICT,
}
