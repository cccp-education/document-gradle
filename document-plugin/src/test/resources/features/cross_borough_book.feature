@cross-borough @book-pipeline
Feature: Codex-to-Document book pipeline
  As a developer using the codex→document book pipeline
  I want codex OCR output pages to assemble correctly via BookAssembler
  So that the cross-borough filesystem contract is validated end-to-end

  Background:
    Given a temporary pages directory simulating codex output

  Scenario: OCR pages from codex assemble into a valid book
    Given the pages directory contains:
      | filename              | content              |
      | 001-intro.adoc        | = Intro\nThis is intro |
      | 002-chapter1.adoc     | = Chapter 1\nContent  |
      | 003-chapter2.adoc     | = Chapter 2\nMore     |
    When I assemble the book with title "FPA Guide" and author "CCCP"
    Then the assembled book should contain 3 page headings
    And the assembled book should contain "Intro"
    And the assembled book should contain "Chapter 1"
    And the assembled book should contain "Chapter 2"
    And the assembled book title should be "FPA Guide"
    And the assembled book author should be "CCCP"
    And the first page heading should be "Intro"
    And the second page heading should be "Chapter 1"
    And the third page heading should be "Chapter 2"

  Scenario: Out-of-order pages are assembled in correct PageOrder
    Given the pages directory contains:
      | filename              | content              |
      | 003-chapter2.adoc     | = Chapter 2\nLast    |
      | 001-intro.adoc        | = Intro\nFirst       |
      | 002-chapter1.adoc     | = Chapter 1\nMiddle  |
    When I assemble the book with title "Ordered" and author "Test"
    Then the assembled book should contain 3 page headings
    And the first page heading should be "Intro"
    And the second page heading should be "Chapter 1"
    And the third page heading should be "Chapter 2"

  Scenario: Empty page is silently excluded from the assembled book
    Given the pages directory contains:
      | filename              | content              |
      | 001-intro.adoc        | = Intro\nValid page  |
      | 002-empty.adoc        |                      |
      | 003-chapter.adoc      | = Chapter\nAlso valid |
    When I assemble the book with title "Degraded" and author "Test"
    Then the assembled book should contain 2 page headings
    And the assembled book should contain "Intro"
    And the assembled book should contain "Chapter"

  Scenario: Non-adoc files in pages directory are ignored
    Given the pages directory contains:
      | filename              | content              |
      | 001-intro.adoc        | = Intro\nValid page  |
      | 002-notes.txt         | Some raw text        |
      | 003-image.png         | binary data          |
      | 004-chapter.adoc      | = Chapter\nContent   |
    When I assemble the book with title "Filtered" and author "Test"
    Then the assembled book should contain 2 page headings
    And the assembled book should contain "Intro"
    And the assembled book should contain "Chapter"
