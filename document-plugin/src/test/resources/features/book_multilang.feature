@book-multilang
Feature: Planning the multi-language translation of a structured book (DOC-BOOK-MULTILANG)
  As a book producer (scanned-content pipeline)
  I want to derive the target languages of a book from the N0 LanguageCatalog
  So that one run produces one translated book per language, structure preserved

  Scenario: An explicit subset is used verbatim, excluding the source language
    Given book-multilang a source language "fr"
    And book-multilang a requested subset "en", "fr", "de"
    When book-multilang the plan is resolved without translateToAll
    Then book-multilang the plan targets "en", "de" in order

  Scenario: translateToAll expands to the whole catalog minus the source
    Given book-multilang a source language "fr"
    When book-multilang the plan is resolved with translateToAll
    Then book-multilang the plan excludes the source "fr"
    And book-multilang the plan contains "en" and "de"

  Scenario: The explicit subset takes precedence over translateToAll
    Given book-multilang a source language "fr"
    And book-multilang a requested subset "de"
    When book-multilang the plan is resolved with translateToAll
    Then book-multilang the plan targets "de"

  Scenario: No knob yields an empty no-op plan
    Given book-multilang a source language "fr"
    When book-multilang the plan is resolved without translateToAll
    Then book-multilang the plan is empty

  Scenario: Each target derives the translated book file name
    Given book-multilang a source language "fr"
    And book-multilang a requested subset "de"
    When book-multilang the plan is resolved without translateToAll
    Then book-multilang target "de" maps to "book-de.adoc" for base "book"
    And book-multilang target "de" maps to "livre-de.adoc" for base "livre"

  Scenario: A code absent from the catalog is ignored
    Given book-multilang a source language "fr"
    And book-multilang a requested subset "en", "xx", "de"
    When book-multilang the plan is resolved without translateToAll
    Then book-multilang the plan targets "en", "de" in order

  Scenario: An RTL language carries its right-to-left flag
    Given book-multilang a source language "fr"
    And book-multilang a requested subset "ar"
    When book-multilang the plan is resolved without translateToAll
    Then book-multilang target "ar" is right-to-left
