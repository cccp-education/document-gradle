package document.semantic

/**
 * Strictness mode applied to the semantic lifting of annotated tables
 * (EPIC DOC-SEMANTIC-TABLE, US-3, decision D8).
 *
 * Symmetric to [document.xref.XrefValidationMode] and [document.ValidationMode]:
 * the projection itself is a pure function of the AST, the mode only changes how
 * the *required-role* findings are surfaced.
 *
 * - [OFF]     : lifting disabled (default — backward-compatible, zero AST read).
 * - [LENIENT] : findings are recorded in the report and logged as warnings; the
 *   build still succeeds.
 * - [STRICT]  : any required-role finding fails the build with a
 *   [org.gradle.api.GradleException] (fail-fast) after the report is written.
 */
enum class TableSemanticsMode {
    OFF,
    LENIENT,
    STRICT,
}
