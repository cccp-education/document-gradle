package document

import document.batch.BatchConvertDocumentsTask
import document.batch.BatchDsl
import document.template.ApplyDocumentTemplateTask
import document.template.TemplateDsl
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
            releaseNotes = ReleaseNotesDsl(
                fromTag = project.objects.property(String::class.java),
                toTag = project.objects.property(String::class.java),
                version = project.objects.property(String::class.java),
                includeDownloads = project.objects.property(Boolean::class.java),
                rendererType = project.objects.property(String::class.java),
                categories = project.objects.mapProperty(String::class.java, String::class.java),
                llmMode = project.objects.property(String::class.java),
            ),
            book = BookDsl(
                pagesDir = project.objects.directoryProperty(),
                photosDir = project.objects.directoryProperty(),
                title = project.objects.property(String::class.java),
                author = project.objects.property(String::class.java),
            ),
            template = TemplateDsl(
                templateFile = project.objects.property(String::class.java),
                variables = project.objects.mapProperty(String::class.java, String::class.java),
                failOnMissingVariable = project.objects.property(Boolean::class.java),
                outputFileName = project.objects.property(String::class.java),
            ),
            batch = BatchDsl(
                sourceDir = project.objects.property(String::class.java),
                outputDir = project.objects.property(String::class.java),
                formats = project.objects.listProperty(String::class.java),
                recursive = project.objects.property(Boolean::class.java),
            ),
        )

        // Conventions (defauts)
        ext.outputDir.convention(project.layout.buildDirectory.dir("docs/document"))
        ext.formats.convention(listOf(DocumentFormat.HTML))
        ext.enrichPlantUml.convention(false)
        ext.enrichImages.convention(false)
        ext.enrichPassthrough.convention(false)
        ext.llmMode.convention("ollama")

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
        // DOC-8 — releaseNotes DSL conventions
        ext.releaseNotes.toTag.convention("HEAD")
        ext.releaseNotes.includeDownloads.convention(true)
        // DOC-8.2 — rendererType null by default (generator falls back to asciidoc)
        ext.releaseNotes.rendererType.convention("asciidoc")
        ext.releaseNotes.categories.convention(emptyMap())
        // DOC-8.4 — llmMode default ollama (only used when rendererType = ollama-asciidoc)
        ext.releaseNotes.llmMode.convention("ollama")
        // DOC-12 extension — book DSL conventions (mirror of legacy flat properties)
        ext.book.title.convention("Untitled Book")
        ext.book.author.convention("Unknown Author")
        // DOC-13 — template DSL conventions
        ext.template.failOnMissingVariable.convention(true)
        ext.template.outputFileName.convention("document")
        ext.template.variables.convention(emptyMap())
        ext.batch.formats.convention(listOf("html"))
        ext.batch.recursive.convention(true)

        // DOC-12 — Mirror the legacy flat enrichment properties from the nested block
        // so both `enrich { plantuml.set(true) }` and the flat `enrichPlantUml.set(true)`
        // paths keep working. The flat properties are the source of truth for the
        // existing task wiring.
        ext.enrichPlantUml.convention(ext.enrich.plantuml)
        ext.enrichImages.convention(ext.enrich.images)
        ext.enrichPassthrough.convention(ext.enrich.passthrough)
        // DOC-12 extension — Mirror the legacy flat book properties from the nested block
        // so both `book { title.set("X") }` and the flat `bookTitle.set("X")` paths work.
        ext.bookPagesDir.convention(ext.book.pagesDir)
        ext.bookPhotosDir.convention(ext.book.photosDir)
        ext.bookTitle.convention(ext.book.title)
        ext.bookAuthor.convention(ext.book.author)
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
        registerDeserializeDocumentConfig(project, ext)
        registerReleaseNotesGenerate(project, ext)
        registerApplyDocumentTemplate(project, ext)
        registerBatchConvertDocuments(project, ext)
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
            task.description = "Produit metadata.json + composite-context.json pour l'integration N3 runner-gradle (assembleCompositeContext). — DOC-6 + DOC-8.3"
            task.outputDir.set(project.layout.buildDirectory.dir("docs/document"))
            task.sourceAdoc.set(cliProp(project, "source").orElse(ext.source.map { it.asFile.name }).orElse("source.adoc"))
            task.releaseNotesDirPath.set(project.layout.buildDirectory.dir("release-notes").map { it.asFile.absolutePath })
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
            // Book (DOC-12 extension — book { } serialisation)
            task.bookPagesDir.set(cliProp(project, "bookPagesDir").map { project.layout.projectDirectory.dir(it).asFile.path }.orElse(ext.bookPagesDir.asFile.map { it.path }))
            task.bookPhotosDir.set(cliProp(project, "bookPhotosDir").map { project.layout.projectDirectory.dir(it).asFile.path }.orElse(ext.bookPhotosDir.asFile.map { it.path }))
            task.bookTitle.set(cliProp(project, "bookTitle").orElse(ext.bookTitle))
            task.bookAuthor.set(cliProp(project, "bookAuthor").orElse(ext.bookAuthor))
        }
    }

    private fun registerDeserializeDocumentConfig(project: Project, ext: DocumentExtension) {
        project.tasks.register("deserializeDocumentConfig", DeserializeDocumentConfigTask::class.java) { task ->
            task.group = "document"
            task.description = "Deserialises document-config.json back to DocumentPipelineConfig and re-serialises it (DOC-12 round-trip validation)."
            val defaultInput = project.layout.buildDirectory.file("docs/document/document-config.json")
            task.inputFile.set(defaultInput)
            project.providers.gradleProperty("document.deserializeInput").orNull?.let { cliPath ->
                task.inputFile.set(project.layout.projectDirectory.file(cliPath))
            }
            task.outputDir.set(project.layout.buildDirectory.dir("docs/document-roundtrip"))
        }
    }

    private fun registerReleaseNotesGenerate(project: Project, ext: DocumentExtension) {
        project.tasks.register("releaseNotesGenerate", ReleaseNotesGenerateTask::class.java) { task ->
            task.group = "document"
            task.description = "Generates release notes AsciiDoc/Markdown/JSON from git log between two tags (DOC-8)."
            task.fromTag.set(cliProp(project, "releaseNotesFromTag").orElse(ext.releaseNotes.fromTag))
            task.toTag.set(cliProp(project, "releaseNotesToTag").orElse(ext.releaseNotes.toTag))
            task.version.set(cliProp(project, "releaseNotesVersion").orElse(ext.releaseNotes.version))
            task.includeDownloads.set(
                cliProp(project, "releaseNotesIncludeDownloads").map { it.toBoolean() }
                    .orElse(ext.releaseNotes.includeDownloads),
            )
            task.rendererType.set(cliProp(project, "releaseNotesRendererType").orElse(ext.releaseNotes.rendererType))
            task.llmMode.set(cliProp(project, "releaseNotesLlmMode").orElse(ext.releaseNotes.llmMode))
            task.categories.set(
                cliProp(project, "releaseNotesCategories")
                    .map { parseCategoriesCli(it) }
                    .orElse(ext.releaseNotes.categories),
            )
            task.outputDir.set(project.layout.buildDirectory.dir("release-notes"))
        }
    }

    private fun parseCategoriesCli(raw: String): Map<String, String> =
        raw.split(",").associate { entry ->
            val (type, label) = entry.split("=", limit = 2)
            type.trim() to label.trim()
        }

    private fun registerApplyDocumentTemplate(project: Project, ext: DocumentExtension) {
        project.tasks.register("applyDocumentTemplate", ApplyDocumentTemplateTask::class.java) { task ->
            task.group = "document"
            task.description = "Applies variable substitution to an AsciiDoc template ({{variable}} syntax). — DOC-13"
            val cliTemplate = cliProp(project, "templateFile").map { project.layout.projectDirectory.file(it) }
            task.templateFile.set(cliTemplate.orElse(project.layout.projectDirectory.file(ext.template.templateFile)))
            task.variables.set(
                cliProp(project, "templateVars")
                    .map { parseTemplateVarsCli(it) }
                    .orElse(ext.template.variables),
            )
            task.failOnMissingVariable.set(
                cliProp(project, "templateFailOnMissing").map { it.toBoolean() }
                    .orElse(ext.template.failOnMissingVariable),
            )
            task.outputFile.set(
                project.layout.buildDirectory.file(
                    project.providers.gradleProperty("document.templateOutputFileName")
                        .orElse(ext.template.outputFileName)
                        .map { "docs/document/$it.adoc" },
                ),
            )
        }
    }

    private fun parseTemplateVarsCli(raw: String): Map<String, String> =
        raw.split(",").associate { entry ->
            val (key, value) = entry.split("=", limit = 2)
            key.trim() to value.trim()
        }

    private fun registerBatchConvertDocuments(project: Project, ext: DocumentExtension) {
        project.tasks.register("batchConvertDocuments", BatchConvertDocumentsTask::class.java) { task ->
            task.group = "document"
            task.description = "Batch-converts all AsciiDoc files in a directory to the specified formats. — DOC-14"
            task.sourceDir.set(
                cliProp(project, "batchSourceDir").map { project.layout.projectDirectory.dir(it) }
                    .orElse(ext.batch.sourceDir.map { project.layout.projectDirectory.dir(it) }),
            )
            task.outputDir.convention(project.layout.buildDirectory.dir("docs/batch"))
            task.formats.set(
                cliProp(project, "batchFormats")
                    .map { it.split(",").map { f -> f.trim() } }
                    .orElse(ext.batch.formats),
            )
            task.recursive.set(
                cliProp(project, "batchRecursive").map { it.toBoolean() }
                    .orElse(ext.batch.recursive),
            )
        }
    }
}