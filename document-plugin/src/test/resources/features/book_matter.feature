@book-matter
Feature: Derived matter policy for structured book assembly (DOC-BOOK-MATTER)
  As a book producer (scanned-content pipeline)
  I want the front/body/back matter of a book to be derived from its table of contents
  So that a body-only TOC is no longer permanently flagged as missing front/back matter

  Scenario: The default policy keeps the 0/9 convention
    Given book-matter a TOC with refs "0.1", "1" and "9.1"
    When book-matter the policy is derived from the TOC
    Then book-matter the policy requires FRONT
    And book-matter the policy requires BACK
    And book-matter ref "0.1" is classified as FRONT
    And book-matter ref "9.1" is classified as BACK
    And book-matter ref "1" is classified as BODY

  Scenario: A body-only TOC requires no matter
    Given book-matter a TOC with refs "1", "1.1", "2" and "2.1"
    When book-matter the policy is derived from the TOC
    Then book-matter the policy requires no FRONT
    And book-matter the policy requires no BACK
    And book-matter ref "1.1" is classified as BODY

  Scenario: A TOC with front matter only does not require back matter
    Given book-matter a TOC with refs "0.1", "1" and "1.1"
    When book-matter the policy is derived from the TOC
    Then book-matter the policy requires FRONT
    And book-matter the policy requires no BACK

  Scenario: A body-only book is structurally valid under the derived policy
    Given book-matter a TOC with refs "1", "1.1", "2" and "2.1"
    When book-matter the structure is validated with the derived policy
    Then book-matter the structure is valid

  Scenario: The legacy policy still flags a missing matter
    Given book-matter a TOC with refs "1", "1.1", "2" and "2.1"
    When book-matter the structure is validated with the legacy policy
    Then book-matter the structure is invalid
    And book-matter the findings mention "no FRONT section"

  Scenario: The NONE policy never requires a matter
    Given book-matter a TOC with refs "1" and "1.1"
    When book-matter the structure is validated with the NONE policy
    Then book-matter the structure is valid

  Scenario: Matter breaks separate the matters of a front-body-back book
    Given book-matter a TOC with refs "0.1", "1" and "9.1"
    And book-matter OCR pages for every referenced section
    When book-matter the book is assembled with matter breaks
    Then book-matter the book carries a break between FRONT and BODY
    And book-matter the book carries a break between BODY and BACK

  Scenario: No matter break is emitted without the opt-in
    Given book-matter a TOC with refs "0.1", "1" and "9.1"
    And book-matter OCR pages for every referenced section
    When book-matter the book is assembled without matter breaks
    Then book-matter the book carries no matter break
