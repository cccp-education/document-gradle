@book-publish
Feature: Planning the publication fan-out of a multi-language book (DOC-BOOK-PUBLISH)
  As a book producer (translated-content pipeline)
  I want to cross the translated languages with the requested output formats
  So that every book-<lang>.adoc is published as book-<lang>.<ext>

  Scenario: Each language is published in every requested format
    Given book-publish the languages "en", "de"
    And book-publish the requested formats "html", "pdf"
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book-en.html", "book-en.pdf", "book-de.html", "book-de.pdf"

  Scenario: An unknown format code is ignored
    Given book-publish the languages "en"
    And book-publish the requested formats "html", "xx", "pdf"
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book-en.html", "book-en.pdf"

  Scenario: A duplicated format is collapsed
    Given book-publish the languages "en"
    And book-publish the requested formats "html", "pdf", "html"
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book-en.html", "book-en.pdf"

  Scenario: The format codes are case-insensitive
    Given book-publish the languages "de"
    And book-publish the requested formats "HTML"
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book-de.html"

  Scenario: No format is a strict no-op
    Given book-publish the languages "en", "de"
    And book-publish the requested formats ""
    When book-publish the publication plan is resolved
    Then book-publish the publication plan is empty

  Scenario: No language is a strict no-op
    Given book-publish the languages ""
    And book-publish the requested formats "html"
    When book-publish the publication plan is resolved
    Then book-publish the publication plan is empty

  Scenario: A target pairs the translated source with the published output
    Given book-publish the languages "en"
    And book-publish the requested formats "epub"
    When book-publish the publication plan is resolved
    Then book-publish the source of "en" in "epub" is "book-en.adoc"
    And book-publish the output of "en" in "epub" is "book-en.epub"

  Scenario: The base name drives the naming convention
    Given book-publish the languages "de"
    And book-publish a base name "livre"
    And book-publish the requested formats "pdf"
    When book-publish the publication plan is resolved
    Then book-publish the source of "de" in "pdf" is "livre-de.adoc"
    And book-publish the output of "de" in "pdf" is "livre-de.pdf"

  Scenario: The source book is not published by default
    Given book-publish the languages "en"
    And book-publish the requested formats "html"
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book-en.html"
    And book-publish no output is the source book

  Scenario: includeSource publishes the assembled source book first
    Given book-publish the languages "en", "de"
    And book-publish the requested formats "html", "pdf"
    And book-publish includeSource is enabled
    When book-publish the publication plan is resolved
    Then book-publish the outputs are "book.html", "book.pdf", "book-en.html", "book-en.pdf", "book-de.html", "book-de.pdf"
    And book-publish the source book is published in every format

  Scenario: includeSource with no format stays a strict no-op
    Given book-publish the languages "en"
    And book-publish the requested formats ""
    And book-publish includeSource is enabled
    When book-publish the publication plan is resolved
    Then book-publish the publication plan is empty
