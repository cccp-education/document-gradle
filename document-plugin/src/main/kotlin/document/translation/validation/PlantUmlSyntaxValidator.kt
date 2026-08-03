package document.translation.validation

import plantuml.service.PlantumlService
import plantuml.validation.SyntaxValidationResult

interface PlantUmlSyntaxValidator {
    fun validate(
        plantumlCode: String,
        articleTitle: String = "",
        blockIndex: Int = 0,
        strategy: String = "",
    ): PlantUmlValidationResult

    companion object {
        fun create(): PlantUmlSyntaxValidator = PlantumlServiceSyntaxValidator()
    }
}

private class PlantumlServiceSyntaxValidator : PlantUmlSyntaxValidator {

    private val plantumlService = PlantumlService()

    override fun validate(
        plantumlCode: String,
        articleTitle: String,
        blockIndex: Int,
        strategy: String,
    ): PlantUmlValidationResult {
        return when (val result = plantumlService.validateSyntax(plantumlCode)) {
            is SyntaxValidationResult.Valid -> PlantUmlValidationResult.Valid
            is SyntaxValidationResult.Invalid -> PlantUmlValidationResult.Invalid(
                articleTitle = articleTitle,
                blockIndex = blockIndex,
                strategy = strategy,
                reason = result.errorMessage,
            )
        }
    }
}
