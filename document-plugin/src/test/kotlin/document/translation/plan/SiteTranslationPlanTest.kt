package document.translation.plan

import document.translation.plantuml.PlantUmlStrategy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteTranslationPlanTest {

    @Test
    fun `valid plan creates successfully`() {
        val plan = SiteTranslationPlan(
            siteName = "cheroliv.com",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar", "zh")
        )
        assertEquals("cheroliv.com", plan.siteName)
        assertEquals("fr", plan.sourceLanguage)
        assertEquals(setOf("en", "ar", "zh"), plan.targetLanguages)
    }

    @Test
    fun `blank siteName throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "", sourceLanguage = "fr", targetLanguages = setOf("en"))
        }
    }

    @Test
    fun `blank sourceLanguage throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "site", sourceLanguage = "", targetLanguages = setOf("en"))
        }
    }

    @Test
    fun `empty targetLanguages throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "site", sourceLanguage = "fr", targetLanguages = emptySet())
        }
    }

    @Test
    fun `unsupported target language throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "site", sourceLanguage = "fr", targetLanguages = setOf("xx"))
        }
    }

    @Test
    fun `unsupported source language throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "site", sourceLanguage = "xx", targetLanguages = setOf("en"))
        }
    }

    @Test
    fun `source language in targetLanguages throws`() {
        assertThrows<IllegalArgumentException> {
            SiteTranslationPlan(siteName = "site", sourceLanguage = "fr", targetLanguages = setOf("fr", "en"))
        }
    }

    @Test
    fun `rtlTargets returns only RTL languages`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar", "zh", "ur", "es")
        )
        assertEquals(setOf("ar", "ur"), plan.rtlTargets())
    }

    @Test
    fun `rtlTargets empty when no RTL language`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "zh", "es")
        )
        assertTrue(plan.rtlTargets().isEmpty())
    }

    @Test
    fun `missingLanguages returns target langs not in existing`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar", "zh", "es")
        )
        assertEquals(setOf("ar", "es"), plan.missingLanguages(setOf("en", "zh")))
    }

    @Test
    fun `missingLanguages empty when all present`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar")
        )
        assertTrue(plan.missingLanguages(setOf("en", "ar")).isEmpty())
    }

    @Test
    fun `isComplete true when existing covers all targets`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar")
        )
        assertTrue(plan.isComplete(setOf("en", "ar", "zh")))
    }

    @Test
    fun `isComplete false when some target missing`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar", "zh")
        )
        assertFalse(plan.isComplete(setOf("en", "ar")))
    }

    @Test
    fun `ltrTargets returns non-RTL target languages`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en", "ar", "zh", "ur")
        )
        assertEquals(setOf("en", "zh"), plan.ltrTargets())
    }

    @Test
    fun `default plantuml strategy is PreserveTechnical`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en")
        )
        assertEquals(PlantUmlStrategy.PreserveTechnical, plan.defaultPlantUmlStrategy)
    }

    @Test
    fun `custom default plantuml strategy is honored`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en"),
            defaultPlantUmlStrategy = PlantUmlStrategy.TranslateLabels
        )
        assertEquals(PlantUmlStrategy.TranslateLabels, plan.defaultPlantUmlStrategy)
    }

    @Test
    fun `withTargetLanguages returns new plan with updated targets`() {
        val plan = SiteTranslationPlan(
            siteName = "site",
            sourceLanguage = "fr",
            targetLanguages = setOf("en")
        )
        val extended = plan.withTargetLanguages(setOf("en", "ar", "zh"))
        assertEquals(setOf("en", "ar", "zh"), extended.targetLanguages)
        assertEquals("site", extended.siteName)
        assertEquals("fr", extended.sourceLanguage)
    }

    @Test
    fun `plans equal when same site source and targets`() {
        val a = SiteTranslationPlan("site", "fr", setOf("en", "ar"))
        val b = SiteTranslationPlan("site", "fr", setOf("ar", "en"))
        assertEquals(a, b)
    }
}
