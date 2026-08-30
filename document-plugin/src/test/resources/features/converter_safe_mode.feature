Feature: Converter safe-mode guard (DOC-CR3-2)

  As a document-gradle user
  I want to configure the AsciidoctorJ SafeMode via the unified `document { converter { } }` DSL
  So that I control filesystem access during conversion (UNSAFE / SERVER / SECURE)

  @converter-safe-mode
  Scenario: converter block SERVER propagates to the convert task
    Given a document gradle project with converter safeMode "SERVER"
    When the plugin is applied and printSafeMode runs
    Then the convertDocumentToHtml task safeMode is "SERVER"

  @converter-safe-mode
  Scenario: converter block SECURE propagates to the convert task
    Given a document gradle project with converter safeMode "SECURE"
    When the plugin is applied and printSafeMode runs
    Then the convertDocumentToHtml task safeMode is "SECURE"

  @converter-safe-mode
  Scenario: default safeMode is UNSAFE when nothing is configured
    Given a document gradle project with no safe mode configuration
    When the plugin is applied and printSafeMode runs
    Then the convertDocumentToHtml task safeMode is "UNSAFE"
