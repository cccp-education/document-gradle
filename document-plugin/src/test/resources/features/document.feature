@document @stub
Feature: Document plugin stub (DOC-1)
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