package document.epub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for the [EpubCheckRunner] port and its sealed results (US-1,
 * EPIC DOC-EPUBCHECK). The domain is Gradle-free — validated with a plain fake,
 * no epubcheck library, no I/O beyond [TempDir] fixtures.
 */
class EpubCheckRunnerTest {

    private class FakeEpubCheckRunner(private val result: EpubValidationResult) : EpubCheckRunner {
        var calledWith: File? = null
        var callCount: Int = 0

        override fun validate(file: File): EpubValidationResult {
            callCount++
            calledWith = file
            return result
        }
    }

    @TempDir
    lateinit var tmp: File

    @Test
    fun `port validates an epub file and returns the configured result`() {
        val epub = File(tmp, "book.epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val runner = FakeEpubCheckRunner(EpubValidationResult.Valid)

        val result = runner.validate(epub)

        assertThat(result).isEqualTo(EpubValidationResult.Valid)
        assertThat(runner.calledWith).isEqualTo(epub)
        assertThat(runner.callCount).isEqualTo(1)
    }

    @Test
    fun `Invalid result carries its issues`() {
        val issues = listOf("OPF-001: missing nav doc")
        val result = EpubValidationResult.Invalid(issues)

        assertThat(result.issues).containsExactly("OPF-001: missing nav doc")
    }

    @Test
    fun `Valid result has no issues`() {
        val valid = EpubValidationResult.Valid

        assertThat(valid.issues).isEmpty()
    }

    @Test
    fun `port is reusable across calls (stateless)`() {
        val epub = File(tmp, "book.epub").apply { writeBytes(byteArrayOf(1)) }
        val runner = FakeEpubCheckRunner(EpubValidationResult.Valid)

        runner.validate(epub)
        val second = runner.validate(epub)

        assertThat(second).isEqualTo(EpubValidationResult.Valid)
        assertThat(runner.callCount).isEqualTo(2)
    }
}