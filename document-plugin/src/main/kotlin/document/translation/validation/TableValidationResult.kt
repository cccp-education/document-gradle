package document.translation.validation

sealed class TableValidationResult {
    data object Valid : TableValidationResult()
    data class Invalid(
        val articleTitle: String,
        val tableIndex: Int,
        val reason: String,
    ) : TableValidationResult()
}
