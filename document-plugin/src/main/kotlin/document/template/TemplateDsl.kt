package document.template

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

class TemplateDsl(
    val templateFile: Property<String>,
    val variables: MapProperty<String, String>,
    val failOnMissingVariable: Property<Boolean>,
    val outputFileName: Property<String>,
)
