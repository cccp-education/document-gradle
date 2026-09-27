package document.ci

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * DOC-CI-ISOLATION (D4) — CI isolation guard.
 *
 * The GitHub runner has no `~/.gradle/gradle.properties`. If
 * `settings.gradle.kts` hard-fails when the credentials are absent, **every**
 * `./gradlew` invocation dies at configuration time and no test ever runs —
 * the whole "Local = Build, Tests = CI" rule is neutralised
 * (bakery `BKY-CI-ISOLATION`, S-243; recurring here since ≥ 2026-09-25).
 *
 * This guard proves the settings script stays tolerant of an absent
 * credential, so an isolated checkout can configure (and therefore test). The
 * complementary barrier (a guard reading a neighbour repository's working
 * tree) is locked by [document.DocumentPluginPublicationTest] no longer
 * resolving `../workspace-bom`.
 */
class SettingsCredentialIsolationTest {

    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    private val settingsText: String
        get() = pluginDir.resolve("settings.gradle.kts").readText(UTF_8)

    @Test
    fun `settings must not hard-fail when credentials are absent`() {
        val verdict = SettingsCredentialPolicy.check(settingsText)

        assertThat(verdict.tolerant)
            .withFailMessage(verdict.message)
            .isTrue()
    }

    @Test
    fun `settings keeps applying the nmcp settings plugin`() {
        // A tolerant settings script must not "fix" CI by dropping publication:
        // the credentials are still read and the central portal still configured.
        assertThat(settingsText).contains("com.gradleup.nmcp.settings")
        assertThat(settingsText).contains("centralPortal")
        assertThat(settingsText).contains("ossrhUsername")
    }
}
