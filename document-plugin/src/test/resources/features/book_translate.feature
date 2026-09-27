@book-translate
Feature: Translating a structured book preserves its structure (DOC-BOOK-TRANSLATE)
  As a book producer (scanned-content pipeline)
  I want to translate the book at its domain level and regenerate the structure
  So that the translated book stays navigable (anchors, doctype, toc, navigation)

  Scenario: Node titles and page bodies are translated while refs are preserved
    Given book-translate a TOC with refs "1", "1.1" and "1.2"
    And book-translate OCR pages for every referenced section
    When book-translate the book is translated from "fr" to "en"
    Then book-translate node "1.1" is titled "Chapter 1.1 [EN]"
    And book-translate the ref "1.1" is preserved
    And book-translate section "1.1" carries the translated body

  Scenario: The regenerated book keeps its anchors, doctype and toc
    Given book-translate a TOC with refs "1", "1.1" and "1.2"
    And book-translate OCR pages for every referenced section
    When book-translate the book is translated from "fr" to "en"
    Then book-translate the regenerated book carries the same number of anchors as the source
    And book-translate the regenerated book carries the doctype
    And book-translate the regenerated book carries the toc block

  Scenario: The navigation labels follow the translated titles
    Given book-translate a TOC with refs "1", "1.1" and "1.2"
    And book-translate OCR pages for every referenced section
    When book-translate the book is translated and assembled with navigation
    Then book-translate section "1.1" links forward to "1.2" using the translated titles

  Scenario: A blank target language is a strict no-op
    Given book-translate a TOC with refs "1", "1.1" and "1.2"
    And book-translate OCR pages for every referenced section
    When book-translate the book is translated from "fr" to ""
    Then book-translate node "1.1" is titled "Chapter 1.1"
    And book-translate section "1.1" carries the original body

  Scenario: An identical source and target language is a no-op
    Given book-translate a TOC with refs "1", "1.1" and "1.2"
    And book-translate OCR pages for every referenced section
    When book-translate the book is translated from "fr" to "fr"
    Then book-translate node "1.1" is titled "Chapter 1.1"
    And book-translate the regenerated book carries the same number of anchors as the source
