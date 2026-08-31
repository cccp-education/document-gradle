@metadata-validation
Feature: Carry the composite validation status into metadata.json (DOC-METADATA-VALIDATION)
  As an integrator feeding runner-gradle N3
  I want collectDocumentRetrieve to signal the overall validation status
  So that downstream consumers know whether the document validated cleanly

  Scenario: Report present and passing yields validationStatus PASS in metadata
    Given a document gradle project with metadata-validation config "STRICT" and source "validateDocumentMetadataValidation"
    When the collectDocumentRetrieve task runs after validateDocument
    Then the metadata.json carries validationStatus PASS

  Scenario: Report present and failing yields validationStatus FAIL in metadata
    Given a document gradle project with metadata-validation config "STRICT" and source "include::/etc/passwd[]"
    When the validateDocument step runs and fails its STRICT build
    And the collectDocumentRetrieve task runs alone
    Then the metadata.json carries validationStatus FAIL

  Scenario: No validation report yields no validationStatus in metadata
    Given a document gradle project with metadata-validation config "OFF" and source "= Legacy\n\nNo validation."
    When the collectDocumentRetrieve task runs alone
    Then the metadata.json omits validationStatus