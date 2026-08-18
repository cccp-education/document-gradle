@frontmatter-retranslate
Feature: Frontmatter re-translation (stale detection + economie d'encre)

  @retranslate @jbake-native
  Scenario: retranslateFrontmatter retranslates stale jbake-summary preserving body
    Given a frontmatter re-translation project with fake LLM
    And a source FR article with jbake summary "Resume FR"
    And a target EN article with the same jbake summary "Resume FR" and an already translated body
    When I am executing the task 'retranslateFrontmatter'
    Then the build should succeed
    And the retranslated target should contain ':jbake-summary: Resume FR [EN]'
    And the retranslated target should preserve the already translated body

  @retranslate @asciidoc-attr
  Scenario: retranslateFrontmatter retranslates stale asciidoc summary without jbake prefix
    Given a frontmatter re-translation project with fake LLM
    And a source FR article with asciidoc summary "Resume FR sans prefixe"
    And a target EN article with the same asciidoc summary "Resume FR sans prefixe" and an already translated body
    When I am executing the task 'retranslateFrontmatter'
    Then the build should succeed
    And the retranslated target should contain ':summary: Resume FR sans prefixe [EN]'
    And the retranslated target should preserve the already translated body

  @retranslate @non-stale
  Scenario: retranslateFrontmatter preserves non-stale frontmatter
    Given a frontmatter re-translation project with fake LLM
    And a source FR article with jbake summary "Resume FR"
    And a target EN article with a translated jbake summary "Summary EN" and an already translated body
    When I am executing the task 'retranslateFrontmatter'
    Then the build should succeed
    And the retranslated target should contain ':jbake-summary: Summary EN'
    And the retranslated target should preserve the already translated body

  @retranslate @stale-title
  Scenario: retranslateFrontmatter retranslates stale title preserving body
    Given a frontmatter re-translation project with fake LLM
    And a source FR article with title "Titre FR non traduit"
    And a target EN article with the same title "Titre FR non traduit" and an already translated body
    When I am executing the task 'retranslateFrontmatter'
    Then the build should succeed
    And the retranslated target should start with '= Titre FR non traduit [EN]'
    And the retranslated target should preserve the already translated body

  @retranslate @stale-description
  Scenario: retranslateFrontmatter retranslates stale jbake-description preserving body
    Given a frontmatter re-translation project with fake LLM
    And a source FR article with jbake description "Description longue FR"
    And a target EN article with the same jbake description "Description longue FR" and an already translated body
    When I am executing the task 'retranslateFrontmatter'
    Then the build should succeed
    And the retranslated target should contain ':jbake-description: Description longue FR [EN]'
    And the retranslated target should preserve the already translated body

  @retranslate @task-registration
  Scenario: retranslateFrontmatter task is registered
    Given a new document project
    When I am executing the task 'tasks' with group 'document'
    Then the output should contain 'retranslateFrontmatter'