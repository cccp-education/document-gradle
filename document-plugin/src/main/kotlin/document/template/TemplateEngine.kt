package document.template

import java.io.File

class TemplateEngine {

    fun apply(template: String, variables: Map<String, String>, failOnMissing: Boolean = true): String {
        val missing = mutableSetOf<String>()
        val result = replaceVariables(template, variables, missing)
        if (failOnMissing && missing.isNotEmpty()) {
            throw MissingVariableException(missing)
        }
        return result
    }

    fun applyFile(templateFile: File, variables: Map<String, String>, failOnMissing: Boolean = true): String {
        val template = templateFile.readText()
        return apply(template, variables, failOnMissing)
    }

    private fun replaceVariables(template: String, variables: Map<String, String>, missing: MutableSet<String>): String {
        val pattern = Regex("""\{\{(\w+)}}""")
        return pattern.replace(template) { match ->
            val key = match.groupValues[1]
            variables[key] ?: run {
                missing.add(key)
                match.value
            }
        }
    }
}

class MissingVariableException(val missingVariables: Set<String>) :
    IllegalStateException("Missing template variables: ${missingVariables.joinToString(", ")}")
