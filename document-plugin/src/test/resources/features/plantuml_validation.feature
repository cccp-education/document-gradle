@plantuml-validation
Feature: PlantUML Validation Pipeline

  As a document translator
  I want to detect corrupted PlantUML diagrams after LLM translation
  So that invalid diagrams are caught before publication

  Background:
    Given a document translator with plantUML validation mode "LENIENT"

  @valid
  Scenario: Valid PlantUML diagram passes validation
    Given an AsciiDoc article with a valid PlantUML diagram
    When I translate the article with plantUML from "fr" to "en"
    Then the plantUML translation should succeed
    And no plantUML validation errors should be reported

  @corrupted-tags
  Scenario: Corrupted PlantUML tags detected in LENIENT mode
    Given a document translator with plantUML validation mode "LENIENT"
    And the plantUML validator always returns invalid
    Given an AsciiDoc article with a PlantUML diagram containing "Utilisateur"
    When I translate the article with plantUML from "fr" to "en"
    Then the plantUML translation should succeed
    And 1 plantUML validation error should be reported
    And the plantUML validation error should mention "Simulated corruption"

  @broken-label
  Scenario: Broken label detected in LENIENT mode
    Given a document translator with plantUML validation mode "LENIENT"
    And the plantUML validator always returns invalid
    Given an AsciiDoc article with a PlantUML diagram containing "Service"
    When I translate the article with plantUML from "fr" to "en"
    Then the plantUML translation should succeed
    And 1 plantUML validation error should be reported
    And the plantUML validation error should mention "Simulated corruption"

  @preserve-technical
  Scenario: PreserveTechnical strategy skips validation
    Given a document translator with plantUML validation mode "LENIENT"
    And the plantUML validator always returns invalid
    Given an AsciiDoc article with a PlantUML diagram having no translatable labels
    When I translate the article with plantUML from "fr" to "en"
    Then the plantUML translation should succeed
    And no plantUML validation errors should be reported

  @strict-mode
  Scenario: Corrupted diagram throws in STRICT mode
    Given a document translator with plantUML validation mode "STRICT"
    And the plantUML validator always returns invalid
    Given an AsciiDoc article with a PlantUML diagram containing "Utilisateur"
    When I translate the article with plantUML from "fr" to "en"
    Then a TranslationException should be thrown for plantUML with message containing "PlantUML validation failed"
