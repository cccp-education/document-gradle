package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task `deserializeDocumentConfig` (DOC-12 round-trip extension).
 *
 * The mirror of [SerializeDocumentConfigTask]: reads a `document-config.json`
 * file produced by `serializeDocumentConfig`, deserialises it back into a
 * [DocumentPipelineConfig] via [DocumentConfigSerializer.deserialize], then
 * re-serialises the result into the output directory. The round-trip is
 * idempotent — the re-serialised JSON is byte-identical to the source JSON
 * (provided the source was produced by `serializeDocumentConfig`).
 *
 * This task validates the bidirectional contract of the N3 pipeline: any
 * downstream consumer (runner-gradle) reading `document-config.json` can
 * reconstruct the pipeline configuration, and re-emitting the configuration
 * reproduces the same contract file. It is the operational proof of the
 * DOC-12 round-trip established at the [DocumentConfigSerializer] level.
 *
 * Loi de l'Economie d'Encre : cette tache ne re-calcule pas de documents,
 * elle valide uniquement que la configuration serialisee est idempotente.
 */
@DisableCachingByDefault(because = "Idempotence is applicative — deterministic JSON round-trip of resolved config")
abstract class DeserializeDocumentConfigTask : DefaultTask() {

    /**
     * The `document-config.json` file to read and round-trip.
     *
     * Defaults to `build/docs/document/document-config.json` (the output of
     * `serializeDocumentConfig`) when not explicitly set.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val inputFile: RegularFileProperty

    /**
     * Directory where the round-tripped `document-config.json` is written.
     *
     * The output file is named `document-config.roundtrip.json` so the
     * round-trip artefact is distinguishable from the source.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "document"
    }

    @TaskAction
    fun execute() {
        val source = inputFile.orNull?.asFile
        if (source == null || !source.exists()) {
            val path = source?.absolutePath ?: "build/docs/document/document-config.json"
            throw IllegalStateException(
                "document-config.json not found at $path — run 'serializeDocumentConfig' first"
            )
        }
        val serializer = DocumentConfigSerializer()
        val baseDir = source.parentFile ?: project.projectDir
        val config = serializer.deserialize(source, baseDir = baseDir)

        val out = outputDir.asFile.get()
        val roundTripped = serializer.serialize(out, config)
        // Rename to make the round-trip artefact distinguishable from the source
        val target = File(out, "document-config.roundtrip.json")
        if (roundTripped != target) {
            roundTripped.renameTo(target)
        }
        logger.lifecycle(
            "[document] deserializeDocumentConfig -> {} ({} bytes, round-trip of {})",
            target.absolutePath,
            target.length(),
            source.absolutePath,
        )
    }
}