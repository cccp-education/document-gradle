package document.releasenotes

import contracts.pipeline.GitLogParser
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesGenerator
import contracts.pipeline.ReleaseNotesRenderer
import java.io.File

/**
 * Default [ReleaseNotesGenerator] — orchestrates a [GitLogParser] and a
 * [ReleaseNotesRenderer] to produce a release notes file on disk.
 *
 * Flow :
 *   1. Resolve [ReleaseNotesConfig.fromTag] (auto-detect if null)
 *   2. Parse commits between fromTag and toTag
 *   3. Render to AsciiDoc (or other configured format)
 *   4. Write to `{outputDir}/release-notes-{version}.adoc`
 *
 * The version is resolved from [ReleaseNotesConfig.version] or auto-detected
 * from the project (VERSION file / gradle.properties).
 */
class GitReleaseNotesGenerator(
    private val projectDir: File,
    override val parser: GitLogParser,
    override val renderer: ReleaseNotesRenderer,
) : ReleaseNotesGenerator {

    override fun generate(config: ReleaseNotesConfig): File {
        val resolvedVersion = config.version ?: parser.detectVersion(projectDir) ?: "SNAPSHOT"
        val fromTag = config.fromTag ?: parser.detectFromTag(projectDir, config.toTag)
        val commits = if (fromTag != null) {
            parser.parse(fromTag, config.toTag)
        } else {
            parser.parse("", config.toTag)
        }
        val outputDir = projectDir.resolve(config.outputDir)
        val outputFile = outputDir.resolve("release-notes-$resolvedVersion.adoc")
        return renderer.renderToFile(commits, config.copy(version = resolvedVersion), outputFile)
    }
}