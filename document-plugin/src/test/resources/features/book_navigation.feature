@book-navigation
Feature: Previous / next navigation links in a structured book (DOC-BOOK-CONSISTENCY-B6)
  As a book producer (scanned-content pipeline)
  I want each emitted section to carry an optional previous / next cross-reference
  So that the reader can navigate the assembled book by following its own links

  Scenario: Navigation links connect a middle section to its neighbours
    Given book-navigation a TOC with refs "1", "1.1" and "1.2"
    And book-navigation OCR pages for every referenced section
    When book-navigation the book is assembled with navigation
    Then book-navigation section "1.1" links back to "1"
    And book-navigation section "1.1" links forward to "1.2"

  Scenario: The first section has no previous link
    Given book-navigation a TOC with refs "1", "1.1" and "1.2"
    And book-navigation OCR pages for every referenced section
    When book-navigation the book is assembled with navigation
    Then book-navigation section "1" has no previous link
    And book-navigation section "1" links forward to "1.1"

  Scenario: The last section has no next link
    Given book-navigation a TOC with refs "1", "1.1" and "1.2"
    And book-navigation OCR pages for every referenced section
    When book-navigation the book is assembled with navigation
    Then book-navigation section "1.2" has no next link
    And book-navigation section "1.2" links back to "1.1"

  Scenario: Navigation is off by default
    Given book-navigation a TOC with refs "1", "1.1" and "1.2"
    And book-navigation OCR pages for every referenced section
    When book-navigation the book is assembled without navigation
    Then book-navigation the book carries no navigation link

  Scenario: A multi-page section links to the next distinct section
    Given book-navigation a TOC with refs "1", "1.1", "1.2" and "1.3"
    And book-navigation a multi-page section "1.1" spanning pages 2 and 3
    When book-navigation the book is assembled with navigation
    Then book-navigation section "1.1" links forward to "1.2"
