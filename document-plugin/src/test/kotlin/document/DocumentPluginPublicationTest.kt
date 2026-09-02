package document

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
 */
class DocumentPluginPublicationTest {
    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    private val rootDir =
        pluginDir.parentFile
            ?: throw IllegalStateException("Cannot resolve repo root from plugin dir")

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

        // Hygiene (D5): local toml self version must match the ws catalog version —
        // the ws catalog (workspace-bom repo) is the cross-borough source of truth.
        val pluginCatalogVersion = documentVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8), local = true)
        val wsCatalogVersion = documentVersionFrom(wsCatalogToml(), local = false)

        assertThat(pluginCatalogVersion)
            .withFailMessage("plugin catalog document version ($pluginCatalogVersion) must match ws catalog document version ($wsCatalogVersion)")
            .isEqualTo(wsCatalogVersion)
    }

    @Test
    fun `workspace bom platform pin matches ws catalog bom version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val wsBomVersion = bomVersionFrom(wsCatalogToml())

        assertThat(buildScript)
            .withFailMessage("workspace-bom platform pin must use the ws catalog BOM version ($wsBomVersion)")
            .contains("""platform("education.cccp:workspace-bom:$wsBomVersion")""")
    }

    /**
     * Reads the `ws` catalog toml resolved by Gradle (module cache) and extracts the
     * `document-plugin` version. Fallback: parse the local MEMPHIS repo toml (same
     * source file as the published catalog).
     */
    private fun wsCatalogToml(): String {
        val wsRepoToml = rootDir.parentFile
            ?.resolve("workspace-bom/gradle/libs.versions.toml")
        if (wsRepoToml != null && wsRepoToml.exists()) return wsRepoToml.readText(UTF_8)
        error("ws catalog toml introuvable — résolution ws impossible pour l'hygiène")
    }

    private fun documentVersionFrom(content: String, local: Boolean): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { if (local) it.startsWith("document =") else it.startsWith("document-plugin =") }
            .substringAfter("\"")
            .substringBefore("\"")

    private fun bomVersionFrom(content: String): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("workspace-bom =") }
            .substringAfter("\"")
            .substringBefore("\"")

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
}