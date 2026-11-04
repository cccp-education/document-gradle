package document.ci

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DOC-TEST-PERF US-3 — guard against a "phantom test" partition.
 *
 * The CI splits the 30 focused Cucumber runners across four shard jobs. This
 * guard proves the partition is **disjoint and exhaustive**: every runner
 * declared in `build.gradle.kts` is assigned to exactly one shard, and the
 * `publish` job needs every test job (a green release cannot skip a shard).
 *
 * Plain JUnit (runs in the `unit` CI job); the workflow and build files are
 * located from the repository root, and the test is skipped when they are not
 * present (isolated module checkout).
 */
class CucumberShardPartitionTest {

    private val buildFile: File get() = File("build.gradle.kts")
    private val workflowFile: File get() = File(repositoryRoot(), ".github/workflows/test.yml")

    private fun repositoryRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, ".github/workflows/test.yml").exists() }
            ?: File("..").absoluteFile

    @Test
    fun `cucumber shards are disjoint and exhaustive`() {
        assumeTrue(buildFile.exists(), "build.gradle.kts not found from ${File("").absolutePath}")
        assumeTrue(workflowFile.exists(), "workflow not found at ${workflowFile.absolutePath}")

        val declared = Regex("""name = "([A-Za-z0-9]+CucumberTest)"""")
            .findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .toSet()
        val workflow = workflowFile.readText()

        assertTrue(declared.size >= 30, "expected the 30 focused Cucumber runners, got ${declared.size}")

        val occurrences = declared.associateWith { name ->
            Regex("""\b${Regex.escape(name)}\b""").findAll(workflow).count()
        }
        val missing = occurrences.filterValues { it == 0 }.keys.sorted()
        val duplicated = occurrences.filterValues { it > 1 }.keys.sorted()

        assertTrue(missing.isEmpty(), "Cucumber runners not assigned to any shard: $missing")
        assertTrue(duplicated.isEmpty(), "Cucumber runners assigned more than once: $duplicated")
    }

    @Test
    fun `publish needs every test job`() {
        assumeTrue(workflowFile.exists(), "workflow not found at ${workflowFile.absolutePath}")

        val publishBlock = workflowFile.readText().substringAfter("  publish:")
        val needs = Regex("""needs:\s*\[([^\]]+)]""").find(publishBlock)?.groupValues?.get(1).orEmpty()

        listOf("static", "unit", "functional", "bdd", "publish-local").forEach { job ->
            assertTrue(needs.contains(job), "publish.needs must include '$job' (got: '$needs')")
        }
    }

    @Test
    fun `the aggregate cucumberTest is out of check`() {
        assumeTrue(buildFile.exists(), "build.gradle.kts not found from ${File("").absolutePath}")

        // DOC-TEST-PERF US-2: the aggregate duplicates the focused runners and
        // must stay wired out of `check` (it remains runnable on demand).
        val build = buildFile.readText()
        assertTrue(
            Regex("""cucumberTest"\)|== "cucumberTest"""").containsMatchIn(build),
            "the build must explicitly unwire the aggregate cucumberTest from check",
        )
        assertEquals(
            1,
            Regex("""is TaskProvider<\*> -> dependency.name == "cucumberTest"""")
                .findAll(build)
                .count(),
            "expected the aggregate guard to be declared once",
        )
    }
}
