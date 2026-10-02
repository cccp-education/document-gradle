Feature: Semantic table lifting (DOC-SEMANTIC-TABLE)

  As a document-gradle user
  I want opt-in annotated AsciiDoc tables projected into typed records
  So that a document can be consumed as structured data (JSON export)

  @table-semantic
  Scenario: LENIENT mode lifts an annotated table into typed records
    Given a document gradle project with tableSemantics "LENIENT" and source "[.semantic-planning,options=\"header\"]\n|===\n| Ref | Titre | Objectif\n\n| 1.0 | Introduction | Situer le cadre\n|===\n"
    When the collectTableSemantics task runs successfully
    Then the table-semantics report contains table "planning" with role "reference"

  @table-semantic
  Scenario: a non-annotated table is ignored
    Given a document gradle project with tableSemantics "LENIENT" and source "|===\n| A | B | C\n\n| 1 | 2 | 3\n|===\n"
    When the collectTableSemantics task runs successfully
    Then the table-semantics report contains no extracted table

  @table-semantic
  Scenario: a blank required role is tolerated in LENIENT mode
    Given a document gradle project with tableSemantics "LENIENT" and source "[.semantic-planning,options=\"header\"]\n|===\n| Ref | Titre | Objectif\n\n| 1.0 | Introduction |\n|===\n"
    When the collectTableSemantics task runs successfully
    Then the table-semantics report records a required-role finding "objective"

  @table-semantic
  Scenario: STRICT mode rejects the build on a blank required role
    Given a document gradle project with tableSemantics "STRICT" and source "[.semantic-planning,options=\"header\"]\n|===\n| Ref | Titre | Objectif\n\n| 1.0 | Introduction |\n|===\n"
    When the collectTableSemantics task runs and fails
    Then the build fails with table semantics message "table semantics validation failed (STRICT)"
    And the table-semantics report records a required-role finding "objective"

  @table-semantic
  Scenario: OFF mode skips lifting silently
    Given a document gradle project with tableSemantics "OFF" and source "[.semantic-planning,options=\"header\"]\n|===\n| Ref | Titre | Objectif\n\n| 1.0 | Introduction | Situer le cadre\n|===\n"
    When the collectTableSemantics task runs successfully
    Then no table-semantics.json is written
