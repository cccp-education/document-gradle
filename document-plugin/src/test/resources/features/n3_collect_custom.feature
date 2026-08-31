Feature: N3 collect follows the outputFileName knob
  As an operator of the document-gradle plugin
  I want the collectDocumentRetrieve task to index artifacts under their real name
  so that composite-context.json N3 references the artifacts actually produced when
  the outputFileName knob renames the conversion output (S-236, follow-up of S-235)

  @n3-collect-custom
  Scenario: Collect indexes a custom-named artifact
    Given an n3-collect document project
    When the HTML conversion runs with a custom output file name
    And the collectDocumentRetrieve task runs with the same custom output file name
    Then n3-collect composite-context.json references custom.html
    And n3-collect composite-context.json does not reference document.html

  @n3-collect-custom
  Scenario: Collect keeps the canonical artifact when no knob is set
    Given an n3-collect document project
    When the HTML conversion runs with the default output file name
    And the collectDocumentRetrieve task runs with default configuration
    Then n3-collect composite-context.json references document.html