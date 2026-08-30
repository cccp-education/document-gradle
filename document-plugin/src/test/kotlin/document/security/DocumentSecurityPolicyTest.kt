package document.security

import org.asciidoctor.SafeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD strict (DOC-CR5-1) — [DocumentSecurityPolicy] DDD pur.
 * RED d'abord (classe absente), puis GREEN (implémentation).
 */
class DocumentSecurityPolicyTest {

    @Test
    fun `UNSAFE plus OFF is Valid (backward-compatible default, both guards disabled)`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.UNSAFE, IncludeGuardMode.OFF)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `UNSAFE plus LENIENT is Warn (asymmetric guard, degraded)`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.UNSAFE, IncludeGuardMode.LENIENT)
        assertTrue(advice is SecurityAdvice.Warn, "attendu Warn, obtenu $advice")
    }

    @Test
    fun `UNSAFE plus STRICT is Reject (asymmetric guard, fail-fast)`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.UNSAFE, IncludeGuardMode.STRICT)
        assertTrue(advice is SecurityAdvice.Reject, "attendu Reject, obtenu $advice")
    }

    @Test
    fun `SERVER plus OFF is Valid`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SERVER, IncludeGuardMode.OFF)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `SERVER plus LENIENT is Valid (coherent — FS restricted + include audited)`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SERVER, IncludeGuardMode.LENIENT)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `SERVER plus STRICT is Valid`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SERVER, IncludeGuardMode.STRICT)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `SECURE plus OFF is Valid`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SECURE, IncludeGuardMode.OFF)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `SECURE plus LENIENT is Valid`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SECURE, IncludeGuardMode.LENIENT)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `SECURE plus STRICT is Valid (fully protected)`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.SECURE, IncludeGuardMode.STRICT)
        assertTrue(advice is SecurityAdvice.Valid, "attendu Valid, obtenu $advice")
    }

    @Test
    fun `Warn reason mentions SafeMode and the active include guard`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.UNSAFE, IncludeGuardMode.LENIENT)
        advice as SecurityAdvice.Warn
        assertTrue(advice.reason.contains("UNSAFE"), advice.reason)
        assertTrue(advice.reason.contains("LENIENT"), advice.reason)
        assertTrue(advice.reason.contains("SafeMode"), advice.reason)
    }

    @Test
    fun `Reject reason mentions SafeMode and STRICT`() {
        val advice = DocumentSecurityPolicy.advise(SafeMode.UNSAFE, IncludeGuardMode.STRICT)
        advice as SecurityAdvice.Reject
        assertTrue(advice.reason.contains("UNSAFE"), advice.reason)
        assertTrue(advice.reason.contains("STRICT"), advice.reason)
        assertTrue(advice.reason.contains("SERVER"), advice.reason)
    }

    @Test
    fun `SecurityAdvice is a sealed family with three variants`() {
        val variants = listOf(
            SecurityAdvice.Valid,
            SecurityAdvice.Warn("x"),
            SecurityAdvice.Reject("y"),
        )
        assertEquals(3, variants.size)
        assertTrue(variants[0] is SecurityAdvice.Valid)
        assertTrue(variants[1] is SecurityAdvice.Warn)
        assertTrue(variants[2] is SecurityAdvice.Reject)
    }
}
