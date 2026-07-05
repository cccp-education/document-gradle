@doc8 @releasenotes
Feature: Release Notes Pipeline (DOC-8 — git log → AsciiDoc)
  As a release manager, I want to generate release notes AsciiDoc
  from the conventional commits between two git tags.

  Scenario: The plugin registers the releaseNotesGenerate task
    Given a new document project
    When I am executing the task 'tasks' with group 'document'
    Then the output should contain 'releaseNotesGenerate'

  Scenario: releaseNotesGenerate produces an AsciiDoc file from conventional commits
    Given a new document project with a git repository
    And a conventional commit "feat: initial feature"
    And a git tag "v1.0.0"
    And a conventional commit "fix(api): correct bug"
    And a conventional commit "docs: update readme"
    When I am executing the task 'releaseNotesGenerate'
    Then the build should succeed
    And the release notes adoc file should exist
    And the release notes adoc should contain 'Release Notes'
    And the release notes adoc should contain 'correct bug (api)'

  Scenario: releaseNotesGenerate uses SNAPSHOT when no version source is available
    Given a new document project with a git repository
    And a conventional commit "feat: first feature"
    When I am executing the task 'releaseNotesGenerate'
    Then the build should succeed
    And the release notes adoc should have the version 'SNAPSHOT' in its filename

  Scenario: releaseNotesGenerate produces a Markdown file when rendererType is markdown
    Given a new document project with a git repository and markdown renderer
    And a conventional commit "feat: initial feature"
    And a git tag "v1.0.0"
    And a conventional commit "fix(api): correct bug"
    When I am executing the task 'releaseNotesGenerate'
    Then the build should succeed
    And the release notes markdown file should exist
    And the release notes markdown should contain '# Release Notes'
    And the release notes markdown should contain '## Corrections'

  Scenario: releaseNotesGenerate uses custom category labels from the DSL
    Given a new document project with a git repository and custom categories
    And a conventional commit "feat: initial feature"
    And a git tag "v1.0.0"
    And a conventional commit "fix: bug fix"
    And a conventional commit "chore: maintenance"
    When I am executing the task 'releaseNotesGenerate'
    Then the build should succeed
    And the release notes adoc should contain '== Bug fixes'
    And the release notes adoc should contain '== Custom chores label'

  @doc83 @n3
  Scenario: collectDocumentRetrieve indexes release notes in composite-context.json
    Given a new document project with a git repository
    And a conventional commit "feat: initial feature"
    And a git tag "v1.0.0"
    And a conventional commit "fix(api): bug fix"
    When I am executing the task 'releaseNotesGenerate'
    And I am executing the task 'collectDocumentRetrieve'
    Then the build should succeed
    And the composite context json file should exist
    And the composite context json should contain the release notes entry
    And the metadata json should contain the release notes path
    And the metadata json should contain the release notes renderer asciidoc

  @doc83 @n3
  Scenario: collectDocumentRetrieve omits release notes path when no release notes generated
    Given a new document project
    When I am executing the task 'collectDocumentRetrieve'
    Then the build should succeed
    And the metadata json file should exist
    And the metadata json should not contain release notes path