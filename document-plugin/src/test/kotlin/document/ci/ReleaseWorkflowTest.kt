package document.ci

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue

/**
 * DOC-CI-ISOLATION-4 — the release gate is a pure function of the workflow text.
 *
 * Publishing to Maven Central must happen **only from a tag**, never from a
 * branch push, and the publishing job must hold everything Central requires:
 * a tag gate, a `needs` on the test job (no publish without green tests), an
 * imported GPG key (Central rejects unsigned artefacts, and the test job has no
 * key), the `CCCP_PUBLISH` signal that re-enables signing on CI
 * ([build.SigningPolicy] lives in the conventions plugin), and the OSSRH
 * credentials from repository secrets.
 */
class ReleaseWorkflowTest {

    private val compliant = """
        name: Test
        on:
          push:
            branches: [main]
            tags: ['v*']
        jobs:
          test:
            runs-on: ubuntu-latest
            steps:
              - run: ./gradlew build
          publish:
            name: Publish to Maven Central
            runs-on: ubuntu-latest
            if: startsWith(github.ref, 'refs/tags/v')
            needs: [test]
            steps:
              - uses: crazy-max/ghaction-import-gpg@v6
                with:
                  gpg_private_key: 0dollar{{ secrets.GPG_PRIVATE_KEY }}
                  passphrase: 0dollar{{ secrets.GPG_PASSPHRASE }}
              - name: Configure Maven Central credentials
                run: |
                  echo "ossrhUsername=0dollar{{ secrets.OSSRH_USERNAME }}" >> gradle.properties
                  echo "ossrhPassword=0dollar{{ secrets.OSSRH_PASSWORD }}" >> gradle.properties
              - name: Publish to Maven Central
                run: ./gradlew publishAggregationToCentralPortal
                env:
                  CI: "true"
                  CCCP_PUBLISH: "true"
    """.replace("0dollar", "$")

    @Test
    fun `a compliant workflow has no violations`() {
        val verdict = ReleaseWorkflow.check(compliant)

        assertThat(verdict.violations).isEmpty()
        assertThat(verdict.compliant).isTrue()
    }

    @Test
    fun `publishing without a tag gate is a violation`() {
        val verdict = ReleaseWorkflow.check(compliant.replace("if: startsWith(github.ref, 'refs/tags/v')", ""))

        assertThat(verdict.compliant).isFalse()
        assertThat(verdict.violations).anyMatch { it.contains("tag", ignoreCase = true) }
    }

    @Test
    fun `publishing without needs on the test job is a violation`() {
        val verdict = ReleaseWorkflow.check(compliant.replace("needs: [test]", ""))

        assertThat(verdict.compliant).isFalse()
        assertThat(verdict.violations).anyMatch { it.contains("needs", ignoreCase = true) }
    }

    @Test
    fun `publishing without a GPG key import is a violation`() {
        val verdict = ReleaseWorkflow.check(compliant.replace("ghaction-import-gpg", "noop"))

        assertThat(verdict.compliant).isFalse()
        assertThat(verdict.violations).anyMatch { it.contains("gpg", ignoreCase = true) }
    }

    @Test
    fun `publishing without the CCCP_PUBLISH signal is a violation`() {
        val verdict = ReleaseWorkflow.check(compliant.replace("CCCP_PUBLISH: \"true\"", ""))

        assertThat(verdict.compliant).isFalse()
        assertThat(verdict.violations).anyMatch { it.contains("CCCP_PUBLISH") }
    }

    @Test
    fun `publishing without the OSSRH secrets is a violation`() {
        val noUser = compliant.replace("secrets.OSSRH_USERNAME", "nope")
        val verdict = ReleaseWorkflow.check(noUser)

        assertThat(verdict.compliant).isFalse()
        assertThat(verdict.violations).anyMatch { it.contains("OSSRH_USERNAME") }
    }

    @Test
    fun `the real workflow is compliant`() {
        val workflow = java.io.File(System.getProperty("user.dir"))
            .absoluteFile
            .parentFile
            .resolve(".github/workflows/test.yml")
            .readText(kotlin.text.Charsets.UTF_8)

        val verdict = ReleaseWorkflow.check(workflow)

        assertTrue(verdict.compliant, verdict.violations.joinToString("\n"))
    }
}
