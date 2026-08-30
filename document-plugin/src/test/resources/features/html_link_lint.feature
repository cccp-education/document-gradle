Feature: HTML link linting of rendered documents

  Lint navigability of a rendered HTML document (EPIC DOC-HTML-LINT) :
  detect dead internal links (href="#id" with no matching anchor) and the
  presence of a table of contents (id="toc"). Symmetric to xref validation
  on the AsciiDoc source, but operates on the generated HTML.

  @html-link-lint @off
  Scenario: OFF mode skips linting
    Given a htmllint html document with mode "OFF" and content "<a href=\"#missing\">dead</a>"
    When the htmllint validation runs
    Then the htmllint outcome is "skip"
    And no htmllint report is written

  @html-link-lint @valid
  Scenario: valid html with a resolved link and a table of contents
    Given a htmllint html document with mode "STRICT" and content "<div id=\"toc\"></div><h2 id=\"a\">A</h2><a href=\"#a\">to a</a>"
    When the htmllint validation runs
    Then the htmllint outcome is "valid"

  @html-link-lint @dead-link-warn
  Scenario: LENIENT dead link warns and writes a report
    Given a htmllint html document with mode "LENIENT" and content "<a href=\"#missing\">dead</a>"
    When the htmllint validation runs
    Then the htmllint outcome is "warn"
    And the htmllint report marks DEAD with "missing"

  @html-link-lint @dead-link-reject
  Scenario: STRICT dead link rejects the build
    Given a htmllint html document with mode "STRICT" and content "<a href=\"#missing\">dead</a>"
    When the htmllint validation runs
    Then the htmllint outcome is "reject"
    And the htmllint build fails with rejection
    And the htmllint report marks DEAD with "missing"

  @html-link-lint @strict-valid
  Scenario: STRICT valid html passes
    Given a htmllint html document with mode "STRICT" and content "<h2 id=\"ok\">Ok</h2><a href=\"#ok\">link</a>"
    When the htmllint validation runs
    Then the htmllint outcome is "valid"
