Feature: Converter include-path guard (DOC-CR4)

  As a document-gradle user
  I want to configure the include-path guard via the unified `document { converter { } }` DSL
  So that untrusted AsciiDoc (OCR/LLM) cannot read files outside the document tree

  @converter-include-guard
  Scenario: converter block STRICT propagates to the convert task
    Given a document gradle project with converter includeGuard "STRICT"
    When the plugin is applied and printIncludeGuard runs
    Then the convertDocumentToHtml task includeGuard is "STRICT"

  @converter-include-guard
  Scenario: converter block LENIENT propagates to the convert task
    Given a document gradle project with converter includeGuard "LENIENT"
    When the plugin is applied and printIncludeGuard runs
    Then the convertDocumentToHtml task includeGuard is "LENIENT"

  @converter-include-guard
  Scenario: default includeGuard is OFF when nothing is configured
    Given a document gradle project with no include guard configuration
    When the plugin is applied and printIncludeGuard runs
    Then the convertDocumentToHtml task includeGuard is "OFF"

  @converter-include-guard
  Scenario: CLI includeGuard overrides the DSL configuration
    Given a document gradle project with converter includeGuard "STRICT"
    When the plugin is applied and printIncludeGuard runs with CLI "LENIENT"
    Then the convertDocumentToHtml task includeGuard is "LENIENT"
