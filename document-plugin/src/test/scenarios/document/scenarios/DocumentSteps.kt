@file:Suppress("unused")

package document.scenarios

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

class DocumentSteps(private val world: DocumentWorld) {

    @Given("a new document project")
    fun createNewDocumentProject() {
        world.createGradleProject()
        assertThat(world.projectDir).exists()
    }

    @When("I am executing the task {string}")
    fun executeTask(taskName: String) {
        world.executeGradle(taskName)
    }

    @When("I am executing the task {string} with group {string}")
    fun executeTaskWithGroup(taskName: String, group: String) {
        world.executeGradle(taskName, "--group", group)
    }

    @Then("the build should succeed")
    fun buildShouldSucceed() {
        assertThat(world.buildResult).isNotNull
    }

    @Then("the output should contain {string}")
    fun outputShouldContain(text: String) {
        assertThat(world.buildResult?.output).contains(text)
    }

    @After
    fun cleanup() {
        world.cleanup()
    }
}