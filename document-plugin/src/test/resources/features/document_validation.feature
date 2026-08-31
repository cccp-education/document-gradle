Feature: Composite document validation (include guard + xref + security policy)
  As an author of untrusted AsciiDoc
  I want a single pre-flight validation pass that aggregates every conversion guard
  So that I get one consolidated report and a clear STRICT/LENIENT/OFF contract

  @document-validation
  Scenario: Valid source under STRICT yields a VALID consolidated report
    Given a document gradle project with document-validation config "STRICT_SERVER" and source "[[intro]]\n== Intro\n\nSee <<intro>> for details.\n"
    When the validateDocument task runs successfully
    Then the document-validation-report.json marks VALID everywhere

  @document-validation
  Scenario: Include path escape under STRICT fails fast with INVALID + REJECT
    Given a document gradle project with document-validation config "STRICT" and source "include::/etc/passwd[]\n"
    When the validateDocument task runs and fails
    Then the document-validation-report.json marks include INVALID
    And the document-validation-report.json marks security REJECT
    And the build fails with document-validation message "include guard"

  @document-validation
  Scenario: Missing cross-reference under STRICT fails fast with MISSING
    Given a document gradle project with document-validation config "STRICT_XREF" and source "= Title\n\nSee <<missing>> here.\n"
    When the validateDocument task runs and fails
    Then the document-validation-report.json lists the missing reference "missing"

  @document-validation
  Scenario: LENIENT warns and writes a report without failing
    Given a document gradle project with document-validation config "LENIENT" and source "include::/etc/passwd[]\n\nSee <<missing>> here.\n"
    When the validateDocument task runs successfully
    Then the document-validation-report.json marks include INVALID
    And the document-validation-report.json marks xref MISSING
    And the document-validation-report.json marks security WARN

  @document-validation
  Scenario: OFF skips every guard but still emits a VALID report
    Given a document gradle project with document-validation config "OFF" and source "include::/etc/passwd[]\n\nSee <<missing>> here.\n"
    When the validateDocument task runs successfully
    Then the document-validation-report.json contains no INVALID or MISSING block

  @document-validation @document-validation-html-lint
  Scenario: HTML lint STRICT fails fast on a dead internal link of the rendered HTML
    Given a document gradle project with document-validationHtml config "STRICT_HTML" and source "= Title\n\n++++\n<p><a href=\"#missing\">gone</a></p>\n++++\n"
    When the validateDocument task runs with the HTML conversion and fails
    Then the document-validation-report.json marks htmlLint DEAD listing "missing"
    And the build fails with document-validation message "HTML link lint"

  @document-validation @document-validation-html-lint
  Scenario: HTML lint VALID when the rendered HTML is navigable
    Given a document gradle project with document-validationHtml config "STRICT_HTML" and source "= Title\n\n[[intro]]\n== Intro\n\nSee <<intro>> here.\n"
    When the validateDocument task runs with the HTML conversion
    Then the document-validation-report.json marks htmlLint VALID

  @document-validation @document-validation-html-lint
  Scenario: HTML lint LENIENT reports DEAD without failing the build
    Given a document gradle project with document-validationHtml config "LENIENT_HTML" and source "= Title\n\n++++\n<p><a href=\"#missing\">gone</a></p>\n++++\n"
    When the validateDocument task runs with the HTML conversion
    Then the document-validation-report.json marks htmlLint DEAD listing "missing"

  @document-validation @document-validation-html-lint @document-validation-custom-output
  Scenario: HTML lint STRICT audits the real custom-named output (outputFileName knob)
    Given a document gradle project with document-validationHtml config "CUSTOM_NAME" and source "= Title\n\n++++\n<p><a href=\"#gone-custom\">gone</a></p>\n++++\n"
    When the validateDocument task runs with the custom output name and fails
    Then the document-validation-report.json marks htmlLint DEAD listing "gone-custom"
    And the document-validation-report.json does not report html-file-missing
    And the build fails with document-validation message "HTML link lint"

  @document-validation @document-validation-html-lint @document-validation-custom-output
  Scenario: HTML lint VALID on a navigable custom-named output (outputFileName knob)
    Given a document gradle project with document-validationHtml config "CUSTOM_NAME" and source "= Title\n\n[[intro]]\n== Intro\n\nSee <<intro>> here.\n"
    When the validateDocument task runs with the custom output name
    Then the document-validation-report.json marks htmlLint VALID
