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