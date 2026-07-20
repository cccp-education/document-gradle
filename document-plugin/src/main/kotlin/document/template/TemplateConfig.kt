package document.template

data class TemplateConfig(
    val templateFile: String,
    val variables: Map<String, String> = emptyMap(),
    val outputFileName: String = "document",
    val failOnMissingVariable: Boolean = true,
)
