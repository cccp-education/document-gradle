Feature: PDF structural validation (DOC-PDF-CHECK)

  As a document-gradle user
  I want the converted PDF artifact to be audited by Apache PDFBox
  So that a published book never ships a corrupt or textless PDF

  @pdf-check
  Scenario: OFF mode skips validation silently
    Given a document gradle project with pdfCheck "OFF" and source "= Title\n\nHello.\n"
    When the validateDocumentPdf pdf-check task runs successfully
    Then no pdf-validation-report.json is written

  @pdf-check
  Scenario: STRICT mode rejects a missing PDF with the pdf-file-missing finding
    Given a document gradle project with pdfCheck "STRICT" and source "= Title\n\nHello.\n"
    When the validateDocumentPdf pdf-check task runs and fails
    Then the build fails with pdf validation message "pdf validation failed (STRICT)"
    And the pdf-check report marks the finding "<pdf-file-missing>"

  @pdf-check
  Scenario: LENIENT mode keeps the build green on a missing PDF
    Given a document gradle project with pdfCheck "LENIENT" and source "= Title\n\nHello.\n"
    When the validateDocumentPdf pdf-check task runs successfully
    Then the pdf-check report marks the finding "<pdf-file-missing>"

  @pdf-check
  Scenario: STRICT mode accepts a freshly converted PDF
    Given a document gradle project with pdfCheck "STRICT" and source "= Book\n\n== Chapter One\n\nHello pdfcheck.\n"
    When the PDF converted and validated with the pdf-check tasks
    Then the pdf-check report marks the finding "VALID"