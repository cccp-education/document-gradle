@table @translation @pipeline
Feature: Table Translation Pipeline

  As a document translator
  I want to translate table content without corrupting structure
  So that translated tables render correctly in AsciiDoc and HTML

  Background:
    Given a table with cols "1,3" and header "Option,Description"
    And a body row with cells "`-c`, `--clean`" and "Clean build directory"

  @happy-path @extract
  Scenario: Extract translatable segments from a table
    When I extract translatable segments
    Then I should get 3 segments
    And segment 0 should have text "Option"
    And segment 1 should have text "Description"
    And segment 2 should have text "Clean build directory"

  @happy-path @reinject
  Scenario: Reinject translations preserves non-translatable structure
    When I extract translatable segments
    And I translate all segments with prefix "TR_"
    And I reinject the translations
    Then cell 0,0 should contain text "TR_Option"
    And cell 0,1 should contain text "TR_Description"
    And cell 1,0 should contain code "-c"
    And cell 1,0 should contain code "--clean"
    And cell 1,1 should contain text "TR_Clean build directory"

  @happy-path @asciidoc
  Scenario: Render table to AsciiDoc round-trip
    When I render the table to AsciiDoc
    Then the AsciiDoc output should contain "[cols=\"1,3\"]"
    And the AsciiDoc output should contain "|==="
    And the AsciiDoc output should contain "|Option "
    And the AsciiDoc output should contain "|`-c`, `--clean` "

  @happy-path @html
  Scenario: Render table to HTML for bakery validation
    When I render the table to HTML
    Then the HTML output should contain "<table>"
    And the HTML output should contain "<thead>"
    And the HTML output should contain "<th>Option</th>"
    And the HTML output should contain "<td><code>-c</code>, <code>--clean</code></td>"
    And the HTML output should contain "</table>"

  @edge-case @escaped-pipe
  Scenario: Table with escaped pipe in cell content
    Given a table with cols "" and header "Expression,Result"
    And a body row with cells "a \\| b" and "pipe preserved"
    When I extract translatable segments
    Then segment 0 should have text "Expression"
    And segment 1 should have text "Result"
    And segment 2 should have text "pipe preserved"

  @edge-case @col-span
  Scenario: Table with column span
    Given a table with cols "1,1,1" and header "A,B,C"
    And a body row with cells "2+| spans two columns" and "E"
    When I render the table to HTML
    Then cell 1,0 should have colspan "2"

  @edge-case @alignment
  Scenario: Table with cell alignment
    Given a table with cols "1,1,1" and header "Left,Center,Right"
    And a body row with cells "<| left", "^| center" and ">| right"
    When I render the table to HTML
    Then cell 1,0 should have style "text-align: left"
    Then cell 1,1 should have style "text-align: center"
    Then cell 1,2 should have style "text-align: right"

  @edge-case @empty-table
  Scenario: Empty table produces valid empty output
    Given an empty table
    When I render the table to AsciiDoc
    Then the AsciiDoc output should contain "|==="
    And the AsciiDoc output should not contain "[cols="
    When I render the table to HTML
    Then the HTML output should contain "<table>"
    And the HTML output should contain "</table>"
