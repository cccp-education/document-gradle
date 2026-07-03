package document

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin Gradle `education.cccp.document` — creation et publication
 * documentaire AsciiDoc multi-format via AsciidoctorJ.
 *
 * DOC-1 (stub) : enregistre l'extension `document { }` et les 8 taches
 * du pipeline. Les conversions (HTML/PDF/EPUB/DocBook/ManPage) sont
 * des stubs no-op ; l'implementation arrive dans DOC-2 -> DOC-5.
 *
 * Ordre de precedent des parametres (pattern planner/codebase) :
 *   CLI (-Pdocument.xxx) > DSL (block document { }) > convention (defaut)
 *
 * Boundary :
 * - Codex (Brooklyn) = READ + RAG — pas de dependance vers codex.
 * - plantuml-gradle (HTOWN) = composition — compileOnly legitime.
 * - planner-gradle (Manhattan) = LLM bridge partage — compileOnly.
 */
class DocumentPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("document", DocumentExtension::class.java)

        // Conventions (defauts)
        ext.outputDir.convention(project.layout.buildDirectory.dir("docs/document"))
        ext.formats.convention(listOf(DocumentFormat.HTML))
        ext.enrichPlantUml.convention(false)
        ext.enrichImages.convention(false)
        ext.enrichPassthrough.convention(false)
        ext.llmMode.convention("ollama")

        registerGenerateDocument(project, ext)
        registerEnrichDocument(project, ext)
        registerConvertTasks(project, ext)
        registerCollectDocumentRetrieve(project, ext)
    }

    private fun cliProp(project: Project, key: String) =
        project.providers.gradleProperty("document.$key")

    private fun cliFile(project: Project, key: String) =
        cliProp(project, key).map { project.layout.projectDirectory.file(it) }

    private fun registerGenerateDocument(project: Project, ext: DocumentExtension) {
        project.tasks.register("generateDocument", GenerateDocumentTask::class.java) { task ->
            task.group = "document"
            task.description = "Genere un document AsciiDoc (IA via koog+langchain4j si prompt set, sinon copie source)."
            val cliSource = cliProp(project, "source").map { project.layout.projectDirectory.file(it) }
            task.sourceFile.set(cliSource.orElse(ext.source))
            task.outputFileName.set(cliProp(project, "outputFileName").orElse("document"))
            task.prompt.set(cliProp(project, "prompt").orElse(ext.prompt))
            task.llmMode.set(cliProp(project, "llmMode").orElse(ext.llmMode))
            task.systemPrompt.set(cliProp(project, "systemPrompt").orElse(ext.systemPrompt))
            task.outputFile.set(project.layout.buildDirectory.file("docs/document/document.adoc"))
        }
    }

    private fun registerEnrichDocument(project: Project, ext: DocumentExtension) {
        project.tasks.register("enrichDocument", EnrichDocumentTask::class.java) { task ->
            task.description = "Enriches the AsciiDoc document (PlantUML, images, includes, passthrough) — DOC-9."
            val cliSource = cliProp(project, "source").map { project.layout.projectDirectory.file(it) }
            task.sourceFile.set(cliSource.orElse(ext.source))
            task.enrichPlantUml.set(cliProp(project, "enrichPlantUml").map { it.toBoolean() }.orElse(ext.enrichPlantUml))
            task.enrichPassthrough.set(cliProp(project, "enrichPassthrough").map { it.toBoolean() }.orElse(ext.enrichPassthrough))
            task.outputFileName.set(cliProp(project, "outputFileName").orElse("document"))
            task.outputFile.set(project.layout.buildDirectory.file("docs/document/document-enriched.adoc"))
        }
    }

    private fun registerConvertTasks(project: Project, ext: DocumentExtension) {
        val conversions = listOf(
            "convertDocumentToHtml" to DocumentFormat.HTML,
            "convertDocumentToPdf" to DocumentFormat.PDF,
            "convertDocumentToEpub" to DocumentFormat.EPUB,
            "convertDocumentToDocBook" to DocumentFormat.DOCBOOK,
            "convertDocumentToManPage" to DocumentFormat.MANPAGE,
        )
        conversions.forEach { (name, format) ->
            project.tasks.register(name, ConvertDocumentTask::class.java) { task ->
                val epicLabel = when (format) {
                    DocumentFormat.HTML -> "DOC-3"
                    DocumentFormat.PDF -> "DOC-4"
                    else -> "DOC-5"
                }
                task.description = "Convertit l'AsciiDoc en ${format.name} (AsciidoctorJ ${format.backend}). — $epicLabel"
                task.sourceFile.set(cliProp(project, "source").map { project.layout.projectDirectory.file(it) }.orElse(ext.source))
                task.format.set(format)
                task.outputFileName.set(cliProp(project, "outputFileName").orElse("document"))
                task.outputFile.set(project.layout.buildDirectory.file("docs/document/document.${format.extension}"))
                task.pdfThemeFile.set(cliFile(project, "pdfTheme").orElse(ext.pdfTheme))
                task.htmlStylesheetFile.set(cliFile(project, "htmlStylesheet").orElse(ext.htmlStylesheet))
                task.epubStylesheetFile.set(cliFile(project, "epubStylesheet").orElse(ext.epubStylesheet))
                task.logoFile.set(cliFile(project, "logo").orElse(ext.logo))
            }
        }
    }

    private fun registerCollectDocumentRetrieve(project: Project, ext: DocumentExtension) {
        project.tasks.register("collectDocumentRetrieve", CollectDocumentRetrieveTask::class.java) { task ->
            task.group = "document"
            task.description = "Produit metadata.json + composite-context.json pour l'integration N3 runner-gradle (assembleCompositeContext). — DOC-6"
            task.outputDir.set(project.layout.buildDirectory.dir("docs/document"))
            task.sourceAdoc.set(cliProp(project, "source").orElse(ext.source.map { it.asFile.name }).orElse("source.adoc"))
        }
    }
}