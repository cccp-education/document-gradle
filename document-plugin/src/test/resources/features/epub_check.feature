Feature: EPUB epubcheck validation (DOC-EPUBCHECK)

  As a document-gradle user
  I want the converted EPUB artifact to be audited by the W3C epubcheck library
  So that a published book never ships a non-conforming EPUB

  @epub-check
  Scenario: OFF mode skips validation silently
    Given a document gradle project with epubCheck "OFF" and source "= Title\n\nHello.\n"
    When the validateDocumentEpub epub-check task runs successfully
    Then no epub-validation-report.json is written

  @epub-check
  Scenario: STRICT mode rejects a missing EPUB with the epub-file-missing finding
    Given a document gradle project with epubCheck "STRICT" and source "= Title\n\nHello.\n"
    When the validateDocumentEpub epub-check task runs and fails
    Then the build fails with epub validation message "epub validation failed (STRICT)"
    And the epub-check report marks the finding "<epub-file-missing>"

  @epub-check
  Scenario: LENIENT mode keeps the build green on a missing EPUB
    Given a document gradle project with epubCheck "LENIENT" and source "= Title\n\nHello.\n"
    When the validateDocumentEpub epub-check task runs successfully
    Then the epub-check report marks the finding "<epub-file-missing>"

  @epub-check
  Scenario: STRICT mode accepts a freshly converted EPUB
    Given a document gradle project with epubCheck "STRICT" and source "= Book\n\n== Chapter One\n\nHello epubcheck.\n"
    When the EPUB converted and validated with the epub-check tasks
    Then the epub-check report marks the finding "VALID"