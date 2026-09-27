package document

import document.ci.PublishedCatalogVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * MEM-CAT-ROLLOUT-1 (S-029, cross-borough MEMPHIS) — publication hygiene guard.
 *
 * D3: the plugin self version is derived from the published workspace catalog
 * (`ws.versions.document.plugin.get()`) — never a duplicated literal.
 * D4: the borough pins the catalog once in settings.gradle.kts.
 * D5 hygiene: the local toml self version and the ws catalog version must agree.
 *
 * DOC-CI-ISOLATION D3 (S-273): the *published* catalog versions are injected by
 * Gradle as system properties (build.gradle.kts). The guard never reads a
 * neighbour repository's working tree (`../workspace-bom/…`) — that was racy
 * between sessions and absent from an isolated CI checkout, which kept the CI
 * red behind the settings barrier (graphify D5-RACE, S-029).
 */
class DocumentPluginPublicationTest {
    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    private val publishedDocumentVersion: String
        get() = PublishedCatalogVersion.require("document.publishedCatalog.documentVersion")

    private val publishedBomVersion: String
        get() = PublishedCatalogVersion.require("document.publishedCatalog.bomVersion")

    @Test
    fun `plugin version matches root consumer catalog version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val versionLine =
            buildScript
                .lineSequence()
                .first { it.trimStart().startsWith("version =") }

        // MEM-CAT-ROLLOUT-1 (D3) — self version derived from the published workspace catalog.
        assertThat(versionLine)
            .withFailMessage("build.gradle.kts version must derive from the published workspace catalog (ws.versions.document.plugin)")
            .contains("ws.versions.document.plugin.get()")

        // Hygiene (D5): local toml self version must match the published ws catalog version —
        // the ws catalog (workspace-bom repo) is the cross-borough source of truth.
        val pluginCatalogVersion = documentVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8))

        assertThat(pluginCatalogVersion)
            .withFailMessage("plugin catalog document version ($pluginCatalogVersion) must match ws catalog document version ($publishedDocumentVersion)")
            .isEqualTo(publishedDocumentVersion)
    }

    @Test
    fun `workspace bom platform pin matches ws catalog bom version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)

        assertThat(buildScript)
            .withFailMessage("workspace-bom platform pin must use the ws catalog BOM version ($publishedBomVersion)")
            .contains("""platform("education.cccp:workspace-bom:$publishedBomVersion")""")
    }

    /**
     * Hygiene (D5) — the local toml must not carry a stale `[versions] workspace-bom`
     * entry that diverges from the published catalog (a 4th source-of-truth drift,
     * piège #13). A dead entry left behind after a pin bump is a trap: it looks like
     * configuration but is never referenced. Either it is absent, or it agrees.
     */
    @Test
    fun `local toml workspace-bom entry must not drift from published catalog`() {
        val localBomVersion =
            workspaceBomVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8))

        if (localBomVersion != null) {
            assertThat(localBomVersion)
                .withFailMessage("local toml workspace-bom ($localBomVersion) must match ws catalog BOM ($publishedBomVersion)")
                .isEqualTo(publishedBomVersion)
        }
    }

    @Test
    fun `plugin group and id are stable for publication`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val pluginId =
            pluginDir
                .resolve("gradle/libs.versions.toml")
                .readText(UTF_8)
                .lineSequence()
                .filter { it.contains("id = \"education.cccp.document\"") }
                .first()
                .substringAfter("id = \"")
                .substringBefore("\"")

        assertThat(buildScript).contains("group = \"education.cccp\"")
        assertThat(pluginId).isEqualTo("education.cccp.document")
    }

    private fun documentVersionFrom(content: String): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("document =") }
            .substringAfter("\"")
            .substringBefore("\"")

    private fun workspaceBomVersionFrom(content: String): String? =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.startsWith("workspace-bom =") }
            ?.substringAfter("\"")
            ?.substringBefore("\"")
}
