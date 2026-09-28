package document.ci

/**
 * DOC-CI-ISOLATION-4 (D7) — release gate policy, a pure function of the CI
 * workflow text.
 *
 * Publishing to Maven Central from CI is a **release** action, so the workflow
 * must make it impossible to publish by accident and possible only when every
 * Central requirement is met. The policy locks five invariants:
 *
 * 1. a **tag gate** (`startsWith(github.ref, 'refs/tags/v')`) — never a branch
 *    push;
 * 2. a `needs` on the test job — never publish without green tests;
 * 3. an imported **GPG key** — Central rejects unsigned artefacts, and a plain
 *    test job has no key (`build.SigningPolicy` skips signing there);
 * 4. the **`CCCP_PUBLISH`** signal — the conventions plugin re-enables signing
 *    on CI only for this job;
 * 5. the **OSSRH credentials** taken from repository secrets.
 *
 * No Gradle type, no I/O: the workflow text is the only input.
 */
object ReleaseWorkflow {

    data class Verdict(val violations: List<String>) {
        val compliant: Boolean get() = violations.isEmpty()
    }

    private val PUBLISH_TASK = Regex("""publishAggregationToCentralPortal""")
    private val TAG_GATE = Regex("""refs/tags/v""")
    private val NEEDS = Regex("""(?m)^\s*needs\s*:""")
    private val GPG = Regex("""ghaction-import-gpg""")
    private val PUBLISH_SIGNAL = Regex("""CCCP_PUBLISH""")
    private val OSSRH_USERNAME = Regex("""OSSRH_USERNAME""")
    private val OSSRH_PASSWORD = Regex("""OSSRH_PASSWORD""")

    fun check(workflowText: String): Verdict {
        val violations = mutableListOf<String>()

        if (!PUBLISH_TASK.containsMatchIn(workflowText)) {
            return Verdict(
                listOf(
                    "the workflow no longer calls 'publishAggregationToCentralPortal' — " +
                        "the release gate only makes sense around a real publish step",
                ),
            )
        }

        if (!TAG_GATE.containsMatchIn(workflowText)) {
            violations +=
                "no tag gate: publishing must be gated by " +
                "'startsWith(github.ref, \\'refs/tags/v\\')', never by a branch push"
        }

        if (!NEEDS.containsMatchIn(workflowText)) {
            violations += "no 'needs' on the publish job: never publish without green tests"
        }

        if (!GPG.containsMatchIn(workflowText)) {
            violations +=
                "no GPG key import: Maven Central rejects unsigned artefacts and a " +
                "plain CI test job has no signing key"
        }

        if (!PUBLISH_SIGNAL.containsMatchIn(workflowText)) {
            violations +=
                "no CCCP_PUBLISH signal: the conventions plugin re-enables signing on " +
                "CI only for the publish job"
        }

        if (!OSSRH_USERNAME.containsMatchIn(workflowText)) {
            violations += "no OSSRH_USERNAME secret wired into the build"
        }

        if (!OSSRH_PASSWORD.containsMatchIn(workflowText)) {
            violations += "no OSSRH_PASSWORD secret wired into the build"
        }

        return Verdict(violations)
    }
}
