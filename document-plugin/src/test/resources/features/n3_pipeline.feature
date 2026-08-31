@n3-pipeline
Feature: Full N3 pipeline carries the composite validation status into metadata.json (S-233)
  As an integrator feeding runner-gradle N3
  I want the staged chain bookPipeline then validateDocument then collectDocumentRetrieve
  to produce a metadata.json whose validationStatus reflects the composite report
  So that the runner-gradle dashboard can trust the freshness of the status

  Scenario: Staged bookPipeline then validateDocument then collect carries validationStatus PASS
    Given a book pipeline project with STRICT xref and HTML lint validation
    When the bookPipeline task runs
    And the validateDocument task runs in a second build
    And the collectDocumentRetrieve task runs in a third build
    Then n3-pipeline metadata.json carries validationStatus PASS