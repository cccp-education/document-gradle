@book-images
Feature: Image-aware book assembly from OCR
  As a book producer turning OCR-ed pages into a navigable book
  I want the page scans to illustrate the book and the ghost images to be repaired
  So that the assembled book is illustrated and the EPUB is epubcheck-valid (no RSC-007)

  Scenario: A page scan is resolved by page number, both paddings probed
    Given book-images a scans directory containing "61.jpg" and "007.png"
    Then book-images the scan of page 61 is "61.jpg"
    And book-images the scan of page 7 is "007.png"
    And book-images the scan of page 42 is absent

  Scenario: A ghost image is repaired with the page scan preserving the directive form
    Given book-images a page "157.adoc" containing
      """
      == Section
      image::qr-code.png[QR Code]
      Suite du texte
      """
    And book-images the page has a scan "157.jpg"
    When book-images the page content is resolved with illustration
    Then book-images the content references the scan "157.jpg"
    And book-images the content no longer references "qr-code.png"

  Scenario: A ghost image is dropped when the page has no scan
    Given book-images a page "042.adoc" containing
      """
      == Section
      image::ghost.png[]
      Suite du texte
      """
    And book-images the page has no scan
    When book-images the page content is resolved with illustration
    Then book-images the content references no image
    And book-images the content still contains "Suite du texte"

  Scenario: An existing image is preserved as-is
    Given book-images a page "020.adoc" containing
      """
      == Section
      image::real.png[Legende]
      Suite
      """
    And book-images the scans directory also contains "real.png"
    When book-images the page content is resolved with illustration
    Then book-images the content references "real.png"

  Scenario: Page scans illustrate the structured book when photos are configured
    Given book-images a TOC with refs "1" and "2"
    And book-images OCR pages for every referenced section
    And book-images every referenced page has a scan
    When book-images the structured book is assembled with illustration
    Then book-images the book emits an imagesdir attribute
    And book-images the book references the scan of every page

  Scenario: Without photos the structured book is unchanged
    Given book-images a TOC with refs "1" and "2"
    And book-images OCR pages for every referenced section
    And book-images every referenced page has a scan
    When book-images the structured book is assembled without illustration
    Then book-images the book emits no imagesdir attribute
    And book-images the book references no image
