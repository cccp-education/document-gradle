@book-validation
Feature: Book validation pipeline (DOC-BOOK-VALIDATE-4)
  As a book producer (FPA pipeline)
  I want the assembled book validated against its table of contents
  So that missing pages, empty sections and orphan pages are caught before publication

  @valid
  Scenario: A complete book passes validation
    Given book-validation a TOC with ref "1" at page 1
    And book-validation a TOC with ref "2" at page 2
    And book-validation an OCR page "001.adoc" with content "Chapter one body."
    And book-validation an OCR page "002.adoc" with content "Chapter two body."
    When book-validation the book is validated
    Then book-validation the result is valid

  @missing-page
  Scenario: A TOC section without a corresponding page is reported
    Given book-validation a TOC with ref "1" at page 1
    And book-validation no OCR page for page 1
    When book-validation the book is validated
    Then book-validation the result is invalid
    And book-validation the result has 1 reason
    And book-validation reason 1 mentions "no .adoc page found"

  @empty-section
  Scenario: A blank OCR page is reported as empty
    Given book-validation a TOC with ref "1" at page 1
    And book-validation an OCR page "001.adoc" with content "   "
    When book-validation the book is validated
    Then book-validation the result is invalid
    And book-validation the result has 1 reason
    And book-validation reason 1 mentions "is empty"

  @toc-mismatch
  Scenario: An OCR page without a TOC section is reported as orphan
    Given book-validation a TOC with ref "1" at page 1
    And book-validation an OCR page "001.adoc" with content "Chapter one body."
    And book-validation an OCR page "099.adoc" with content "Orphan page body."
    When book-validation the book is validated
    Then book-validation the result is invalid
    And book-validation the result has 1 reason
    And book-validation reason 1 mentions "no corresponding TOC section"

  @strict
  Scenario: An invalid book fails the build in STRICT mode
    Given book-validation a TOC with ref "1" at page 1
    And book-validation no OCR page for page 1
    When book-validation the book is validated in "STRICT" mode
    Then book-validation a build failure is raised mentioning "no .adoc page found"
