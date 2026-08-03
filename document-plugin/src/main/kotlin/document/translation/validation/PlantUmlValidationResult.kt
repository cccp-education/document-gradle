package document.translation.validation

sealed class PlantUmlValidationResult {
    data object Valid : PlantUmlValidationResult()
    data class Invalid(
        val articleTitle: String,
        val blockIndex: Int,
        val strategy: String,
        val reason: String,
    ) : PlantUmlValidationResult()
}
