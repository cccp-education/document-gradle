@cross-borough @book-pipeline
Feature: Codex-to-Document book pipeline (faithful filesystem contract)
  As a developer using the codex->document book pipeline
  I want codex raw OCR pages to be assembled faithfully by the TOC-driven
  document pipeline
  So that the cross-borough filesystem contract is validated end-to-end

  Background:
    Given a temporary pages directory simulating codex output

  Scenario: Raw codex pages assemble as a flat book without fabricated headings
    Given the pages directory contains:
      | filename          | content                   |
      | 001-intro.adoc    | This is the introduction. |
      | 002-chapter1.adoc | This is chapter one.      |
      | 003-chapter2.adoc | This is chapter two.      |
    When I assemble the flat book with title "Content Guide" and author "CCCP"
    Then the assembled book should contain 0 section headings
    And the assembled book should contain "This is the introduction."
    And the assembled book should contain "This is chapter one."
    And the assembled book should contain "This is chapter two."
    And the assembled book title should be "Content Guide"
    And the assembled book author should be "CCCP"

  Scenario: Out-of-order codex pages are assembled in page order
    Given the pages directory contains:
      | filename          | content              |
      | 003-chapter2.adoc | This is chapter two. |
      | 001-intro.adoc    | This is intro.       |
      | 002-chapter1.adoc | This is chapter one. |
    When I assemble the flat book with title "Ordered" and author "Test"
    Then the assembled book should contain "This is intro." before "This is chapter one."
    And the assembled book should contain "This is chapter one." before "This is chapter two."

  Scenario: TOC-driven assembly derives headings from the TOC, not the page content
    Given the pages directory contains:
      | filename          | content                   |
      | 001-intro.adoc    | This is the introduction. |
      | 002-chapter1.adoc | This is chapter one.      |
    And a codex TOC file:
      """
      | Reference | Subject / Title | Page | File
      | 1 | Introduction | 1 | 001-intro.adoc
      | 2 | Chapter One | 2 | 002-chapter1.adoc
      """
    When I assemble the book from the TOC with title "Content Guide" and author "CCCP"
    Then the assembled book should contain 2 section headings
    And the assembled book should contain the heading "== 1. Introduction"
    And the assembled book should contain the heading "== 2. Chapter One"
    And the assembled book should carry the anchor "[[1]]"
    And the assembled book should carry the anchor "[[2]]"
    And the assembled book should contain "This is the introduction."
    And the assembled book should contain "This is chapter one."

  Scenario: A multi-page TOC section concatenates its codex pages under one heading
    Given the pages directory contains:
      | filename       | content                 |
      | 001-intro.adoc | First part of section.  |
      | 002-intro.adoc | Second part of section. |
    And a codex TOC file:
      """
      | Reference | Subject / Title | Page | File
      | 1 | Introduction | 1, 2 | 001-intro.adoc, 002-intro.adoc
      """
    When I assemble the book from the TOC with title "Content Guide" and author "CCCP"
    Then the assembled book should contain 1 section headings
    And the assembled book should contain the heading "== 1. Introduction"
    And the assembled book should contain "First part of section." before "Second part of section."

  Scenario: A codex page missing for a TOC section degrades without failing
    Given the pages directory contains:
      | filename       | content              |
      | 001-intro.adoc | This is the intro.   |
    And a codex TOC file:
      """
      | Reference | Subject / Title | Page | File
      | 1 | Introduction | 1 | 001-intro.adoc
      | 2 | Conclusion | 2 | 002-conclusion.adoc
      """
    When I assemble the book from the TOC with title "Degraded" and author "Test"
    Then the assembled book should contain 2 section headings
    And the assembled book should contain "This is the intro."

  Scenario: Non-adoc files in the pages directory are ignored
    Given the pages directory contains:
      | filename         | content         |
      | 001-intro.adoc   | This is intro.  |
      | 002-notes.txt    | Some raw text   |
      | 003-image.png    | binary data     |
      | 004-chapter.adoc | This is chapter. |
    When I assemble the flat book with title "Filtered" and author "Test"
    Then the assembled book should contain "This is intro."
    And the assembled book should contain "This is chapter."
    And the assembled book should not contain "Some raw text"
