@table-validation
Feature: Table Validation Pipeline

  As a document translator
  I want to detect corrupted tables after LLM translation
  So that invalid tables are caught before publication

  Background:
    Given a document translator with validation mode "LENIENT"

  @valid
  Scenario: Valid table passes validation
    Given an AsciiDoc article with a valid table
    When I translate the article from "fr" to "en"
    Then the translation should succeed
    And no validation errors should be reported

  @corrupted-delimiter
  Scenario: Corrupted delimiter detected in LENIENT mode
    Given a document translator with validation mode "LENIENT"
    And the translation service corrupts "Clean build directory" to "Clean |=== build directory"
    Given an AsciiDoc article with a table containing "Clean build directory"
    When I translate the article from "fr" to "en"
    Then the translation should succeed
    And 1 validation error should be reported
    And the validation error should mention "|==="

  @corrupted-delimiter @strict
  Scenario: Corrupted delimiter throws in STRICT mode
    Given a document translator with validation mode "STRICT"
    And the translation service corrupts "Clean build directory" to "Clean |=== build directory"
    Given an AsciiDoc article with a table containing "Clean build directory"
    When I translate the article from "fr" to "en"
    Then a TranslationException should be thrown with message containing "|==="

  @column-mismatch
  Scenario: Column count mismatch detected
    Given an AsciiDoc article with a table having mismatched column counts
    When I translate the article from "fr" to "en"
    Then the translation should succeed
    And 1 validation error should be reported
    And the validation error should mention "column count mismatch"

  @cols-mismatch
  Scenario: Cols spec mismatch detected
    Given an AsciiDoc article with a table having cols "2,3" but 3 columns
    When I translate the article from "fr" to "en"
    Then the translation should succeed
    And 1 validation error should be reported
    And the validation error should mention "cols count"

  @batch-report
  Scenario: Batch translation produces validation report
    Given a batch translator with 3 articles including 1 with a corrupted table
    When I run batch translation from "fr" to "en"
    Then a table-validation-report.json should be produced
    And the report should contain 1 invalid entry
