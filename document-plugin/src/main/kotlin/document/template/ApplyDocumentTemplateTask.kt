package document.template

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

@DisableCachingByDefault(because = "Template output depends on variable values which may change between builds")
abstract class ApplyDocumentTemplateTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templateFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val variables: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val failOnMissingVariable: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "document"
        description = "Applies variable substitution to an AsciiDoc template ({{variable}} syntax)."
        failOnMissingVariable.convention(true)
    }

    @TaskAction
    fun applyTemplate() {
        val logger = LoggerFactory.getLogger(ApplyDocumentTemplateTask::class.java)
        val template = templateFile.get().asFile
        val output = outputFile.get().asFile
        val vars = variables.getOrElse(emptyMap())
        val failOnMissing = failOnMissingVariable.get()

        val engine = TemplateEngine()
        val result = engine.applyFile(template, vars, failOnMissing)

        output.parentFile.mkdirs()
        output.writeText(result)
        logger.info("applyDocumentTemplate — {} -> {} ({} variables)", template.name, output.absolutePath, vars.size)
    }
}
