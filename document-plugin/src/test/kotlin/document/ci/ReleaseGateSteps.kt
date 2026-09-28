package document.ci

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step definitions for the `@release-gate` scenarios (DOC-CI-ISOLATION-4).
 *
 * Pure BDD over the [ReleaseWorkflow] domain — no Gradle task, no I/O. Every
 * step text carries the `release-gate ` prefix (anti-glue-collision pattern,
 * S-088/S-223).
 */
class ReleaseGateSteps {

    private val tagGate = "if: startsWith(github.ref, 'refs/tags/v')"
    private val needs = "needs: [test]"
    private val gpg = "uses: crazy-max/ghaction-import-gpg@v6"
    private val publishSignal = "CCCP_PUBLISH: \"true\""
    private val ossrh = """
        ossrhUsername=0dollar{{ secrets.OSSRH_USERNAME }}
        ossrhPassword=0dollar{{ secrets.OSSRH_PASSWORD }}
    """.trimIndent().replace("0dollar", "$")
    private val publishTask = "run: ./gradlew publishAggregationToCentralPortal"

    private val base = """
        jobs:
          test:
            steps:
              - run: ./gradlew build
          publish:
            $tagGate
            $needs
            steps:
              - $gpg
              - name: Configure Maven Central credentials
                run: |
                  $ossrh
              - name: Publish to Maven Central
                $publishTask
                env:
                  CI: "true"
                  $publishSignal
    """.trimIndent()

    private var workflow: String = base
    private var drop: String = ""
    private var verdict: ReleaseWorkflow.Verdict? = null

    @Given("release-gate a complete release workflow")
    fun `release-gate a complete release workflow`() {
        workflow = base
        drop = ""
    }

    @Given("release-gate a release workflow missing {string}")
    fun `release-gate a release workflow missing`(element: String) {
        drop = element
        workflow =
            when (element) {
                "the tag gate" -> base.replace(tagGate, "")
                "the needs on the test job" -> base.replace(needs, "")
                "the GPG key import" -> base.replace(gpg, "")
                "the CCCP_PUBLISH signal" -> base.replace(publishSignal, "")
                "the OSSRH secrets" -> base.replace(ossrh, "")
                "the publish task" -> base.replace(publishTask, "run: ./gradlew build")
                else -> error("unknown element '$element'")
            }
    }

    @When("release-gate the workflow is audited")
    fun `release-gate the workflow is audited`() {
        verdict = ReleaseWorkflow.check(workflow)
    }

    @Then("release-gate the workflow is compliant")
    fun `release-gate the workflow is compliant`() {
        val v = requireNotNull(verdict) { "no verdict — run 'the workflow is audited' first" }
        assertTrue(v.compliant, v.violations.joinToString("\n"))
    }

    @Then("release-gate the workflow is non compliant")
    fun `release-gate the workflow is non compliant`() {
        val v = requireNotNull(verdict) { "no verdict — run 'the workflow is audited' first" }
        assertFalse(v.compliant, "expected a violation for '$drop', got none")
    }
}
