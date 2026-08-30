Feature: Cross-reference validation (DOC-XREF-VALIDATE)

  As a document-gradle user
  I want unresolved AsciiDoc cross-references (<<id>> / xref:id[]) to be detected
  So that a published book never ships dead navigation links

  @xref-validation
  Scenario: OFF mode skips validation silently
    Given a document gradle project with xrefValidation "OFF" and source "[[intro]]Intro\n\nSee <<intro>>.\n"
    When the validateDocumentXref task runs successfully
    Then no xref-validation-report.json is written

  @xref-validation
  Scenario: LENIENT mode writes a VALID report when all references resolve
    Given a document gradle project with xrefValidation "LENIENT" and source "[[intro]]Intro\n\nSee <<intro>> and xref:intro[Intro].\n"
    When the validateDocumentXref task runs successfully
    Then the report marks VALID

  @xref-validation
  Scenario: LENIENT mode never fails the build on an unresolved reference
    Given a document gradle project with xrefValidation "LENIENT" and source "= Title\n\nSee <<missing>> here.\n"
    When the validateDocumentXref task runs successfully
    Then the report lists the missing reference "missing"

  @xref-validation
  Scenario: STRICT mode rejects the build on an unresolved reference
    Given a document gradle project with xrefValidation "STRICT" and source "= Title\n\nSee <<missing>> here.\n"
    When the validateDocumentXref task runs and fails
    Then the build fails with xref validation message "xref validation failed (STRICT)"
    And the report lists the missing reference "missing"

  @xref-validation
  Scenario: STRICT mode accepts a fully resolved document
    Given a document gradle project with xrefValidation "STRICT" and source "[[intro]]Intro\n\nSee <<intro>> and xref:intro[Intro].\n"
    When the validateDocumentXref task runs successfully
    Then the report marks VALID
