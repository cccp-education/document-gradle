package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentEnrichConfigTest {

    @Test
    fun `DocumentEnrichConfig default has all flags false`() {
        val config = DocumentEnrichConfig()
        assertFalse(config.plantuml)
        assertFalse(config.images)
        assertFalse(config.passthrough)
    }

    @Test
    fun `DocumentEnrichConfig requiresEnrichment is false when all flags false`() {
        val config = DocumentEnrichConfig()
        assertFalse(config.requiresEnrichment())
    }

    @Test
    fun `DocumentEnrichConfig requiresEnrichment is true when plantuml is true`() {
        val config = DocumentEnrichConfig(plantuml = true)
        assertTrue(config.requiresEnrichment())
    }

    @Test
    fun `DocumentEnrichConfig requiresEnrichment is true when images is true`() {
        val config = DocumentEnrichConfig(images = true)
        assertTrue(config.requiresEnrichment())
    }

    @Test
    fun `DocumentEnrichConfig requiresEnrichment is true when passthrough is true`() {
        val config = DocumentEnrichConfig(passthrough = true)
        assertTrue(config.requiresEnrichment())
    }

    @Test
    fun `DocumentEnrichConfig stores all three flags`() {
        val config = DocumentEnrichConfig(plantuml = true, images = true, passthrough = true)
        assertTrue(config.plantuml)
        assertTrue(config.images)
        assertTrue(config.passthrough)
    }
}