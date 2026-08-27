package document

/**
 * Validation strictness mode applied to an assembled book.
 *
 * [LENIENT] logs validation findings as warnings and never fails the build
 * (default — protects the book assembly from spurious failures on imperfect
 * OCR corpora). [STRICT] fails the build with a [org.gradle.api.GradleException]
 * when the [BookValidator] reports any inconsistency.
 *
 * Ink Economy Law: validation is deterministic and side-effect free; the mode
 * only changes how findings are surfaced (log vs build failure).
 */
enum class ValidationMode {
    LENIENT,
    STRICT,
}
