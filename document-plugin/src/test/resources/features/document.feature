@document @stub
Feature: Document plugin (DOC-1 stub + DOC-2 generation IA)
  En tant que formateur, je veux que le plugin document s'applique
  et enregistre les taches du pipeline documentaire.

  Scenario: Le plugin s'applique sur un projet vierge
    Given a new document project
    When I am executing the task 'tasks'
    Then the build should succeed

  Scenario: Le plugin enregistre la tache generateDocument
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'generateDocument'

  Scenario: Le plugin enregistre la tache convertDocumentToPdf
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'convertDocumentToPdf'

  Scenario: Le plugin enregistre 8 taches documentaires
    Given a new document project
    When I am executing the task 'tasks' with group 'document'
    Then the output should contain 'generateDocument'
    And the output should contain 'enrichDocument'
    And the output should contain 'convertDocumentToHtml'
    And the output should contain 'convertDocumentToPdf'
    And the output should contain 'convertDocumentToEpub'
    And the output should contain 'convertDocumentToDocBook'
    And the output should contain 'convertDocumentToManPage'
    And the output should contain 'collectDocumentRetrieve'

  @doc2 @generation
  Scenario: generateDocument produit un AsciiDoc valide depuis un prompt via LLM fake
    Given a new document project with fake LLM
    When I am executing the task 'generateDocument' with prompt 'Genere une introduction'
    Then the build should succeed
    And the generated document should be a valid AsciiDoc

  @doc2 @generation
  Scenario: generateDocument skip si la sortie existe deja pour le meme prompt
    Given a new document project with fake LLM and existing output
    When I am executing the task 'generateDocument' with prompt 'Genere une introduction'
    Then the build should succeed

  @doc3 @conversion
  Scenario: convertDocumentToHtml produit un fichier HTML5 valide depuis un AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist
    And the converted HTML should contain a doctype declaration
    And the converted HTML should contain the source title

  @doc3 @conversion
  Scenario: convertDocumentToHtml skip si la sortie existe et la source est inchangee
    Given a new document project with an AsciiDoc source and an existing HTML output
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist

  @doc4 @conversion
  Scenario: convertDocumentToPdf produit un fichier PDF valide depuis un AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToPdf'
    Then the build should succeed
    And the converted PDF file should exist
    And the converted PDF should be a valid PDF document

  @doc4 @conversion
  Scenario: convertDocumentToPdf skip si la sortie existe et la source est inchangee
    Given a new document project with an AsciiDoc source and an existing PDF output
    When I am executing the task 'convertDocumentToPdf'
    Then the build should succeed
    And the converted PDF file should exist

  @doc5 @conversion
  Scenario: convertDocumentToEpub produit un fichier EPUB3 valide depuis un AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should be a valid EPUB document

  @doc5 @conversion
  Scenario: convertDocumentToEpub skip si la sortie existe et la source est inchangee
    Given a new document project with an AsciiDoc source and an existing EPUB output
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist

  @doc5 @conversion
  Scenario: convertDocumentToDocBook produit un fichier DocBook 5 valide depuis un AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToDocBook'
    Then the build should succeed
    And the converted DocBook file should exist
    And the converted DocBook should be a valid DocBook document

  @doc5 @conversion
  Scenario: convertDocumentToManPage produit une page de manuel valide depuis un AsciiDoc source
    Given a new document project with an AsciiDoc manpage source
    When I am executing the task 'convertDocumentToManPage'
    Then the build should succeed
    And the converted ManPage file should exist
    And the converted ManPage should be a valid manpage document