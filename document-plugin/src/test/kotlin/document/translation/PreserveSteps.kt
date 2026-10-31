package document.translation

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals

/**
 * MEM-I18N-PRESERVE / PRESERVE-2 — `@preserve` scenarios.
 *
 * Step wording is prefixed with "preserve" so it stays unique in the
 * `document.translation` glue (anti-collision rule S-088).
 */
class PreserveSteps {
    private var block: String = ""

    @Given("a preserve block {string}")
    fun aPreserveBlock(value: String) {
        block = value
    }

    @When("the preserve classifier classifies the block")
    fun thePreserveClassifierClassifiesTheBlock() {
        // The classification is a pure function: nothing to prepare.
    }

    @Then("the block is not translatable")
    fun theBlockIsNotTranslatable() {
        assertEquals(false, TextTranslatableClassifier.isTranslatable(block), block)
    }

    @Then("the block is translatable")
    fun theBlockIsTranslatable() {
        assertEquals(true, TextTranslatableClassifier.isTranslatable(block), block)
    }
}
