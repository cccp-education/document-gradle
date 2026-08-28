@book-tree
Feature: Structured book assembly from the table-of-contents tree
  As a book producer (FPA pipeline)
  I want the book assembler to emit a structured, navigable AsciiDoc book from the TOC tree
  So that the PDF/EPUB export carries a real hierarchy (parts, chapters, sections)

  Scenario: Structured tree is assembled from the TOC refs
    Given book-tree a TOC with refs "0.1", "1", "1.1", "1.2" and "9.1"
    And book-tree OCR pages for every referenced section
    When book-tree the book is assembled from the tree
    Then book-tree the title page is emitted with author "Cheroliv"
    And book-tree section "1" is emitted as a level-1 heading
    And book-tree section "1.1" is emitted as a level-2 heading
    And book-tree every emitted section carries its cross-reference anchor

  Scenario: Front, body and back matter are emitted in document order
    Given book-tree a TOC with front, body and back matter sections
    And book-tree OCR pages for every referenced section
    When book-tree the book is assembled from the tree
    Then book-tree the front matter precedes the body
    And book-tree the body precedes the back matter

  Scenario: Hierarchical numbering prefixes every heading
    Given book-tree a TOC with refs "1", "1.1" and "1.1.1"
    And book-tree OCR pages for every referenced section
    When book-tree the book is assembled from the tree
    Then book-tree the heading of section "1" starts with "1. "
    And book-tree the heading of section "1.1" starts with "1.1. "
    And book-tree the heading of section "1.1.1" starts with "1.1.1. "

  Scenario: Navigation gives the previous and next sections in document order
    Given book-tree a TOC with refs "1", "1.1" and "1.2"
    When book-tree the navigation of section "1.1" is computed
    Then book-tree the previous section is "1"
    And book-tree the next section is "1.2"

  Scenario: The first and last sections bound the navigation
    Given book-tree a TOC with refs "1", "1.1" and "1.2"
    When book-tree the navigation of section "1" is computed
    Then book-tree the previous section is absent
    When book-tree the navigation of section "1.2" is computed
    Then book-tree the next section is absent

  Scenario: Pages without a TOC still assemble as a flat blob
    Given book-tree OCR pages without any TOC
    When book-tree the book is assembled without a tree
    Then book-tree no structured hierarchical heading is emitted
    And book-tree every page content is concatenated in page order
