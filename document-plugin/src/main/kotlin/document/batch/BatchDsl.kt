package document.batch

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

class BatchDsl(
    val sourceDir: Property<String>,
    val outputDir: Property<String>,
    val formats: ListProperty<String>,
    val recursive: Property<Boolean>,
)
