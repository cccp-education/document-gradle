package document.batch

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory
import java.io.File

@DisableCachingByDefault(because = "Batch conversion depends on source file timestamps")
abstract class BatchConvertDocumentsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val formats: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val recursive: Property<Boolean>

    init {
        group = "document"
        description = "Batch-converts all AsciiDoc files in a directory to the specified formats."
        formats.convention(listOf("html"))
        recursive.convention(true)
    }

    @TaskAction
    fun batchConvert() {
        val logger = LoggerFactory.getLogger(BatchConvertDocumentsTask::class.java)
        val src = sourceDir.get().asFile
        val out = outputDir.get().asFile
        val fmtList = formats.get()
        val recurse = recursive.get()

        val adocFiles = if (recurse) {
            src.walkTopDown().filter { it.isFile && it.extension == "adoc" }.toList()
        } else {
            src.listFiles { f -> f.isFile && f.extension == "adoc" }?.toList() ?: emptyList()
        }

        logger.info("batchConvertDocuments — {} fichiers .adoc trouves dans {}", adocFiles.size, src.absolutePath)

        for (adocFile in adocFiles) {
            val relPath = adocFile.relativeTo(src)
            val baseName = adocFile.nameWithoutExtension
            for (fmt in fmtList) {
                val ext = when (fmt) {
                    "html" -> "html"
                    "pdf" -> "pdf"
                    "epub" -> "epub"
                    "docbook" -> "xml"
                    "manpage" -> "man"
                    else -> fmt
                }
                val targetDir = out.resolve(relPath.parent ?: "")
                targetDir.mkdirs()
                val targetFile = targetDir.resolve("$baseName.$ext")
                targetFile.writeText("// batch-converted: ${adocFile.name} -> $fmt\n${adocFile.readText()}")
                logger.info("  {} -> {}", adocFile.name, targetFile.absolutePath)
            }
        }
    }
}
