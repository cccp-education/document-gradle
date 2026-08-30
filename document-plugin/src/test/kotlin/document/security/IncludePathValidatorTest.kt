package document.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD strict (DOC-CR4-1) — [IncludePathValidator] DDD pur.
 * RED d'abord (classes absentes), puis GREEN (implémentation).
 */
class IncludePathValidatorTest {

    private val baseDir = File("/tmp/doc-cr4-base").also { it.mkdirs() }

    @Test
    fun `parseIncludes finds a single include with its line`() {
        val text = "= Title\n\ninclude::chap1.adoc[]\n\ncontent\n"
        val includes = IncludePathValidator.parseIncludes(text)
        assertEquals(1, includes.size)
        assertEquals("chap1.adoc", includes.first().target)
        assertEquals(3, includes.first().line)
    }

    @Test
    fun `parseIncludes returns empty when no include`() {
        val text = "= Title\n\njust text\n"
        assertTrue(IncludePathValidator.parseIncludes(text).isEmpty())
    }

    @Test
    fun `parseIncludes captures options block after target`() {
        val text = "include::chap1.adoc[lines=1..10]\n"
        val includes = IncludePathValidator.parseIncludes(text)
        assertEquals(1, includes.size)
        assertEquals("chap1.adoc", includes.first().target)
    }

    @Test
    fun `parseIncludes handles quoted target`() {
        val text = "include::\"my chapter.adoc\"[]\n"
        val includes = IncludePathValidator.parseIncludes(text)
        assertEquals(1, includes.size)
        assertEquals("my chapter.adoc", includes.first().target)
    }

    @Test
    fun `validate accepts relative include inside allowedRoot`() {
        val text = "include::chap1.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Valid, "attendu Valid, obtenu $result")
    }

    @Test
    fun `validate accepts include in a subdirectory of allowedRoot`() {
        val text = "include::parts/chap1.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Valid, "attendu Valid, obtenu $result")
    }

    @Test
    fun `validate rejects path traversal escaping allowedRoot`() {
        val text = "include::../secret.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
        result as IncludeValidationResult.Invalid
        assertTrue(result.reason.contains("escapes") || result.reason.contains("traversal"), result.reason)
        assertEquals("../secret.adoc", result.offendingTarget)
    }

    @Test
    fun `validate rejects absolute path`() {
        val text = "include::/etc/passwd[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
        result as IncludeValidationResult.Invalid
        assertTrue(result.reason.contains("absolute"), result.reason)
    }

    @Test
    fun `validate reports the offending line of a traversal`() {
        val text = "= Title\n\n\ninclude::../../etc/secret.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
        result as IncludeValidationResult.Invalid
        assertEquals(4, result.line)
    }

    @Test
    fun `validate reports first invalid among multiple includes`() {
        val text = "include::ok.adoc[]\ninclude::../bad.adoc[]\ninclude::alsook.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
        result as IncludeValidationResult.Invalid
        assertEquals("../bad.adoc", result.offendingTarget)
    }

    @Test
    fun `validate does not allow traversal nested in subdirectory`() {
        val text = "include::parts/../../secret.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
    }

    @Test
    fun `validate allows include exactly at allowedRoot boundary`() {
        val text = "include::./local.adoc[]\n"
        val result = IncludePathValidator.validate(text, baseDir)
        assertTrue(result is IncludeValidationResult.Valid, "attendu Valid, obtenu $result")
    }

    @Test
    fun `validate supports a custom allowedRoot`() {
        val allowed = File("/tmp/doc-cr4-allowed").also { it.mkdirs() }
        val text = "include::../outside.adoc[]\n"
        // allowedRoot == allowed, baseDir == allowed too -> ../outside still escapes
        val result = IncludePathValidator.validate(text, allowed, allowed)
        assertTrue(result is IncludeValidationResult.Invalid, "attendu Invalid, obtenu $result")
        // when baseDir is a subdir of allowed, include to sibling is fine
        val sub = File(allowed, "sub").also { it.mkdirs() }
        val ok = IncludePathValidator.validate("include::../sibling.adoc[]\n", sub, allowed)
        assertTrue(ok is IncludeValidationResult.Valid, "attendu Valid, obtenu $ok")
    }
}
