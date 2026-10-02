package document

import document.semantic.AsciidoctorTableReader
import document.semantic.SemanticExtraction
import document.semantic.SemanticExtractor
import document.semantic.SemanticTableSchema
import document.semantic.TableSemanticsMode
import document.semantic.TableSemanticsReport
import org.asciidoctor.SafeMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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

/**
 * Lifts opt-in annotated AsciiDoc tables into typed records and exports
 * `table-semantics.json` (EPIC DOC-SEMANTIC-TABLE, US-3, decisions D7/D8/D9).
 *
 * The task is *thin* : it delegates the AST access to the [AsciidoctorTableReader]
 * adapter (dependency inversion, decision D6) and the projection to the pure
 * [SemanticExtractor] domain (decision D5). Severity mirrors
 * [document.xref.XrefValidationTask] :
 * - [TableSemanticsMode.OFF]     : no-op (default — backward-compatible, no AST
 *   read, no artifact — Ink Economy Law) ;
 * - [TableSemanticsMode.LENIENT] : findings are logged as warnings, the report
 *   is written, the build succeeds ;
 * - [TableSemanticsMode.STRICT]  : the report is written first, then any
 *   required-role finding fails the build with a [GradleException] (fail-fast).
 *
 * The task is read-only on the AsciiDoc source (Règle 7) : the AST is loaded,
 * never converted in place.
 */
@DisableCachingByDefault(because = "Semantic lifting is an opt-in audit whose severity branches are side-effect-bearing (fail-fast vs report)")
abstract class CollectTableSemanticsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val tableSemantics: Property<TableSemanticsMode>

    @get:Input
    @get:Optional
    abstract val schemas: ListProperty<SemanticTableSchema>

    /**
     * AsciidoctorJ safe-mode applied to the AST load (decision D11) — the same
     * as the conversion. Defaults to [SafeMode.UNSAFE] (backward-compatible).
     */
    @get:Input
    @get:Optional
    abstract val safeMode: Property<SafeMode>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    private val log = LoggerFactory.getLogger(CollectTableSemanticsTask::class.java)

    @TaskAction
    fun collect() {
        val mode = tableSemantics.get()
        if (mode == TableSemanticsMode.OFF) {
            log.info("{} — semantic lifting disabled (OFF), skipping (no AST read)", name)
            return
        }

        val source = sourceFile.get().asFile
        if (!source.exists() || !source.isFile) {
            log.warn("{} — source absente : {}", name, source.absolutePath)
            return
        }

        val declaredSchemas = schemas.getOrElse(emptyList())
        val reader = AsciidoctorTableReader(safeMode.getOrElse(SafeMode.UNSAFE))
        val extractions = SemanticExtractor.extract(reader.read(source), declaredSchemas)
        val report = TableSemanticsReport.fromExtractions(mode, declaredSchemas, extractions)

        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.toJson())
        }

        val extracted = extractions.count { it is SemanticExtraction.Extracted }
        val skipped = extractions.count { it is SemanticExtraction.Skipped }

        when (mode) {
            TableSemanticsMode.LENIENT -> {
                if (skipped > 0) {
                    log.warn("{} — {} annotated table(s) without a declared schema: {}", name, skipped, report.skipped.joinToString { it.name })
                }
                if (report.findings.isNotEmpty()) {
                    log.warn("{} — {} required-role finding(s)", name, report.findings.size)
                }
            }
            TableSemanticsMode.STRICT -> {
                if (report.findings.isNotEmpty()) {
                    throw GradleException(
                        "table semantics validation failed (STRICT): ${report.findings.size} required-role " +
                            "finding(s): ${report.findings.joinToString { "${it.table}[${it.rowIndex}].${it.role}" }}",
                    )
                }
            }
            TableSemanticsMode.OFF -> Unit
        }

        logger.lifecycle(
            "[document] collectTableSemantics — {} table(s) extracted, {} skipped, {} finding(s) → {}",
            extracted,
            skipped,
            report.findings.size,
            reportFile.get().asFile.absolutePath,
        )
    }
}
