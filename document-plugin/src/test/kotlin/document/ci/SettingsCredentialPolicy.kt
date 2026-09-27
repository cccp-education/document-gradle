package document.ci

/**
 * DOC-CI-ISOLATION (D4) — purity guard over the borough's own settings script.
 *
 * The CI runner has no `~/.gradle/gradle.properties`. A settings script that
 * hard-fails on a missing credential (`?: error("ossrhUsername not found")`)
 * makes **every** invocation — `./gradlew build` included — fail at
 * configuration time, before a single test runs. The whole "Local = Build,
 * Tests = CI" rule is then neutralised and the downstream debt is masked
 * (bakery `BKY-CI-ISOLATION`, S-243; the same failure mode recurred here for
 * ≥ 2026-09-25).
 *
 * Decision D2: credentials are **optional at configuration time** — nmcp is
 * configured with an empty fallback (`?: ""`, bakery-proven) or left
 * unconfigured behind an explicit `if`. Only the real
 * `publishAggregationToCentralPortal` needs them, and it fails later, clearly,
 * on a machine that has no secrets — never at configuration.
 *
 * The policy is a pure function of the settings text so it is unit-testable
 * without a Gradle build (no Gradle types, no I/O).
 */
object SettingsCredentialPolicy {

    /** The historical hard-fail this guard forbids. */
    private val HARD_FAIL = Regex("""\?:\s*error\s*\(\s*["']ossrh(Username|Password)""")

    private val OSSRH_KEY = Regex("""ossrh(Username|Password)""")

    private val NMCP_SETTINGS_PLUGIN = Regex("""com\.gradleup\.nmcp\.settings""")

    private val CENTRAL_PORTAL = Regex("""centralPortal""")

    data class Verdict(val tolerant: Boolean, val message: String)

    /**
     * @return a tolerant verdict when the settings script configures nmcp
     *   without hard-failing on an absent credential.
     */
    fun check(settingsText: String): Verdict {
        val hardFail = HARD_FAIL.find(settingsText)
        if (hardFail != null) {
            return Verdict(
                false,
                "settings.gradle.kts hard-fails on a missing credential " +
                    "('${hardFail.value}…') — an isolated CI checkout (no " +
                    "~/.gradle/gradle.properties) cannot even configure the build. " +
                    "Use a tolerant fallback (D2: '?: \"\"' or an explicit if-guard).",
            )
        }

        if (!OSSRH_KEY.containsMatchIn(settingsText)) {
            return Verdict(
                false,
                "settings.gradle.kts no longer reads the ossrh credentials at all — " +
                    "publishing would silently lose its identity. Configuration must " +
                    "stay tolerant, not absent.",
            )
        }

        if (!NMCP_SETTINGS_PLUGIN.containsMatchIn(settingsText)) {
            return Verdict(
                false,
                "settings.gradle.kts no longer applies 'com.gradleup.nmcp.settings' — " +
                    "the publishing path was removed instead of made tolerant.",
            )
        }

        if (!CENTRAL_PORTAL.containsMatchIn(settingsText)) {
            return Verdict(
                false,
                "settings.gradle.kts no longer configures the central portal — " +
                    "nmcp settings were removed instead of made tolerant.",
            )
        }

        return Verdict(true, "settings.gradle.kts configures nmcp tolerantly (CI-safe).")
    }
}
