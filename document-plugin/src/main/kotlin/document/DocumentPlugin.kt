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

        // DOC-12 — Initialise the nested DSL blocks via ObjectFactory so the
        // concrete `val` properties resolve in the Kotlin DSL.
        ext.initNested(
            enrich = DocumentEnrichDsl(
                plantuml = project.objects.property(Boolean::class.java),
                images = project.objects.property(Boolean::class.java),
                passthrough = project.objects.property(Boolean::class.java),
            ),
            outputs = DocumentOutputsDsl(
                html = project.objects.property(Boolean::class.java),
                pdf = project.objects.property(Boolean::class.java),
                epub = project.objects.property(Boolean::class.java),
                docbook = project.objects.property(Boolean::class.java),
                manpage = project.objects.property(Boolean::class.java),
            ),
            metadata = DocumentMetadataDsl(
                title = project.objects.property(String::class.java),
                author = project.objects.property(String::class.java),
                language = project.objects.property(String::class.java),
            ),
        )

        // Conventions (defauts)
        ext.outputDir.convention(project.layout.buildDirectory.dir("docs/document"))
        ext.formats.convention(listOf(DocumentFormat.HTML))
        ext.enrichPlantUml.convention(false)
        ext.enrichImages.convention(false)
        ext.enrichPassthrough.convention(false)
        ext.llmMode.convention("ollama")
        ext.bookTitle.convention("Untitled Book")
        ext.bookAuthor.convention("Unknown Author")

        // DOC-12 — Nested DSL block conventions (unified document { }).
        ext.enrich.plantuml.convention(false)
        ext.enrich.images.convention(false)
        ext.enrich.passthrough.convention(false)
        ext.outputs.html.convention(true)
        ext.outputs.pdf.convention(false)
        ext.outputs.epub.convention(false)
        ext.outputs.docbook.convention(false)
        ext.outputs.manpage.convention(false)
        ext.metadata.title.convention("Untitled Document")
        ext.metadata.author.convention("Unknown Author")
        ext.metadata.language.convention("fr")

        // DOC-12 — Mirror the legacy flat enrichment properties from the nested block
        // so both `enrich { plantuml.set(true) }` and the flat `enrichPlantUml.set(true)`
        // paths keep working. The flat properties are the source of truth for the
        // existing task wiring.
        ext.enrichPlantUml.convention(ext.enrich.plantuml)
        ext.enrichImages.convention(ext.enrich.images)
        ext.enrichPassthrough.convention(ext.enrich.passthrough)
        // DOC-12 — Mirror outputs flags back into the legacy formats list so the
        // existing conversion tasks remain single-source-of-truth.
        ext.formats.convention(
            project.provider {
                buildList {
                    if (ext.outputs.html.get()) add(DocumentFormat.HTML)
                    if (ext.outputs.pdf.get()) add(DocumentFormat.PDF)
                    if (ext.outputs.epub.get()) add(DocumentFormat.EPUB)
                    if (ext.outputs.docbook.get()) add(DocumentFormat.DOCBOOK)
                    if (ext.outputs.manpage.get()) add(DocumentFormat.MANPAGE)
                }
            },
        )

        registerGenerateDocument(project, ext)
        registerEnrichDocument(project, ext)
        registerConvertTasks(project, ext)
        registerCollectDocumentRetrieve(project, ext)
        registerAssembleBook(project, ext)
        registerBookPipeline(project, ext)
        registerSerializeDocumentConfig(project, ext)
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

    private fun registerAssembleBook(project: Project, ext: DocumentExtension) {
        project.tasks.register("assembleBook", AssembleBookTask::class.java) { task ->
            task.description = "Assembles OCR-ed AsciiDoc pages (codex-gradle output) into a single book. — DOC-11"
            task.pagesDir.set(cliProp(project, "bookPagesDir").map { project.layout.projectDirectory.dir(it) }.orElse(ext.bookPagesDir))
            task.photosDir.set(cliProp(project, "bookPhotosDir").map { project.layout.projectDirectory.dir(it) }.orElse(ext.bookPhotosDir))
            task.title.set(cliProp(project, "bookTitle").orElse(ext.bookTitle))
            task.author.set(cliProp(project, "bookAuthor").orElse(ext.bookAuthor))
            task.outputFileName.set(cliProp(project, "outputFileName").orElse("book"))
            task.outputFile.set(project.layout.buildDirectory.file("docs/document/book.adoc"))
        }
    }

    private fun registerBookPipeline(project: Project, ext: DocumentExtension) {
        project.tasks.register("bookPipeline") { task ->
            task.group = "document"
            task.description = "Composite book pipeline: assemble -> enrich -> HTML/PDF/EPUB -> collect. — DOC-11"
        }
        project.tasks.named("bookPipeline").configure { task ->
            task.dependsOn(
                "assembleBook",
                "enrichDocument",
                "convertDocumentToHtml",
                "convertDocumentToPdf",
                "convertDocumentToEpub",
                "collectDocumentRetrieve",
            )
        }
        val assembleBook = project.tasks.named("assembleBook")
        val enrichDocument = project.tasks.named("enrichDocument")
        val html = project.tasks.named("convertDocumentToHtml")
        val pdf = project.tasks.named("convertDocumentToPdf")
        val epub = project.tasks.named("convertDocumentToEpub")
        val collect = project.tasks.named("collectDocumentRetrieve")
        enrichDocument.configure { it.mustRunAfter(assembleBook) }
        html.configure { it.mustRunAfter(enrichDocument) }
        pdf.configure { it.mustRunAfter(enrichDocument) }
        epub.configure { it.mustRunAfter(enrichDocument) }
        collect.configure { it.mustRunAfter(listOf(html, pdf, epub)) }
    }

    private fun registerSerializeDocumentConfig(project: Project, ext: DocumentExtension) {
        project.tasks.register("serializeDocumentConfig", SerializeDocumentConfigTask::class.java) { task ->
            task.group = "document"
            task.description = "Serialises the unified document { } DSL configuration to document-config.json (N3 contract). — DOC-12"
            val cliSource = cliProp(project, "source").map { project.layout.projectDirectory.file(it) }
            task.sourceFile.set(cliSource.orElse(ext.source))
            task.outputDir.set(project.layout.buildDirectory.dir("docs/document"))
            // Flat enrichment flags (legacy + nested mirror)
            task.enrichPlantUml.set(cliProp(project, "enrichPlantUml").map { it.toBoolean() }.orElse(ext.enrichPlantUml))
            task.enrichImages.set(cliProp(project, "enrichImages").map { it.toBoolean() }.orElse(ext.enrichImages))
            task.enrichPassthrough.set(cliProp(project, "enrichPassthrough").map { it.toBoolean() }.orElse(ext.enrichPassthrough))
            // Outputs flags
            task.outputHtml.set(cliProp(project, "outputHtml").map { it.toBoolean() }.orElse(ext.outputs.html))
            task.outputPdf.set(cliProp(project, "outputPdf").map { it.toBoolean() }.orElse(ext.outputs.pdf))
            task.outputEpub.set(cliProp(project, "outputEpub").map { it.toBoolean() }.orElse(ext.outputs.epub))
            task.outputDocbook.set(cliProp(project, "outputDocbook").map { it.toBoolean() }.orElse(ext.outputs.docbook))
            task.outputManpage.set(cliProp(project, "outputManpage").map { it.toBoolean() }.orElse(ext.outputs.manpage))
            // Theme files
            task.pdfThemeFile.set(cliFile(project, "pdfTheme").orElse(ext.pdfTheme))
            task.htmlStylesheetFile.set(cliFile(project, "htmlStylesheet").orElse(ext.htmlStylesheet))
            task.epubStylesheetFile.set(cliFile(project, "epubStylesheet").orElse(ext.epubStylesheet))
            task.logoFile.set(cliFile(project, "logo").orElse(ext.logo))
            // Metadata
            task.metaTitle.set(cliProp(project, "metaTitle").orElse(ext.metadata.title))
            task.metaAuthor.set(cliProp(project, "metaAuthor").orElse(ext.metadata.author))
            task.metaLanguage.set(cliProp(project, "metaLanguage").orElse(ext.metadata.language))
        }
    }
}