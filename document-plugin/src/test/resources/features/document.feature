@document @stub
Feature: Document plugin (DOC-1 stub + DOC-2 IA generation)
  As a trainer, I want the document plugin to apply and register the
  document pipeline tasks.

  Scenario: The plugin applies on a fresh project
    Given a new document project
    When I am executing the task 'tasks'
    Then the build should succeed

  Scenario: The plugin registers the generateDocument task
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'generateDocument'

  Scenario: The plugin registers the convertDocumentToPdf task
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'convertDocumentToPdf'

  Scenario: The plugin registers 8 document tasks
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
  Scenario: generateDocument produces a valid AsciiDoc from a prompt via fake LLM
    Given a new document project with fake LLM
    When I am executing the task 'generateDocument' with prompt 'Generate an introduction'
    Then the build should succeed
    And the generated document should be a valid AsciiDoc

  @doc2 @generation
  Scenario: generateDocument skips when the output already exists for the same prompt
    Given a new document project with fake LLM and existing output
    When I am executing the task 'generateDocument' with prompt 'Generate an introduction'
    Then the build should succeed

  @doc3 @conversion
  Scenario: convertDocumentToHtml produces a valid HTML5 file from an AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist
    And the converted HTML should contain a doctype declaration
    And the converted HTML should contain the source title

  @doc3 @conversion
  Scenario: convertDocumentToHtml skips when the output exists and the source is unchanged
    Given a new document project with an AsciiDoc source and an existing HTML output
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist

  @doc4 @conversion
  Scenario: convertDocumentToPdf produces a valid PDF file from an AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToPdf'
    Then the build should succeed
    And the converted PDF file should exist
    And the converted PDF should be a valid PDF document

  @doc4 @conversion
  Scenario: convertDocumentToPdf skips when the output exists and the source is unchanged
    Given a new document project with an AsciiDoc source and an existing PDF output
    When I am executing the task 'convertDocumentToPdf'
    Then the build should succeed
    And the converted PDF file should exist

  @doc5 @conversion
  Scenario: convertDocumentToEpub produces a valid EPUB3 file from an AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should be a valid EPUB document

  @doc5 @conversion
  Scenario: convertDocumentToEpub skips when the output exists and the source is unchanged
    Given a new document project with an AsciiDoc source and an existing EPUB output
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist

  @doc5 @conversion
  Scenario: convertDocumentToDocBook produces a valid DocBook 5 file from an AsciiDoc source
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToDocBook'
    Then the build should succeed
    And the converted DocBook file should exist
    And the converted DocBook should be a valid DocBook document

  @doc5 @conversion
  Scenario: convertDocumentToManPage produces a valid man page from an AsciiDoc source
    Given a new document project with an AsciiDoc manpage source
    When I am executing the task 'convertDocumentToManPage'
    Then the build should succeed
    And the converted ManPage file should exist
    And the converted ManPage should be a valid manpage document

  @doc9 @enrichment
  Scenario: enrichDocument preserves inline plantuml blocks without escaping
    Given a new document project with an AsciiDoc source containing a plantuml block
    When I am executing the task 'enrichDocument'
    Then the build should succeed
    And the enriched document should exist
    And the enriched document should preserve the plantuml block

  @doc9 @enrichment
  Scenario: enrichDocument preserves raw HTML passthrough blocks without escaping
    Given a new document project with an AsciiDoc source containing a passthrough block
    When I am executing the task 'enrichDocument'
    Then the build should succeed
    And the enriched document should exist
    And the enriched document should preserve the passthrough block

  @doc9 @enrichment
  Scenario: enrichDocument recursively resolves AsciiDoc includes
    Given a new document project with an AsciiDoc source containing an include directive
    When I am executing the task 'enrichDocument'
    Then the build should succeed
    And the enriched document should exist
    And the enriched document should contain the included content

  @doc10 @theme
  Scenario: The document DSL accepts a theme block with pdfTheme and htmlStylesheet
    Given a new document project with an AsciiDoc source and a custom theme
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist
    And the converted HTML should contain the custom stylesheet link

  @doc10 @theme
  Scenario: convertDocumentToPdf applies the configured YML theme
    Given a new document project with an AsciiDoc source and a custom theme
    When I am executing the task 'convertDocumentToPdf'
    Then the build should succeed
    And the converted PDF file should exist

  @doc10 @theme
  Scenario: convertDocumentToHtml succeeds without a configured theme (default fallback)
    Given a new document project with an AsciiDoc source
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist
    And the converted HTML should contain a doctype declaration

  @doc6 @n3
  Scenario: collectDocumentRetrieve produces metadata json and composite-context json after conversion
    Given a new document project with an AsciiDoc source and a converted HTML artifact
    When I am executing the task 'collectDocumentRetrieve'
    Then the build should succeed
    And the metadata json file should exist
    And the composite context json file should exist
    And the metadata json should contain source new-orleans
    And the composite context json should contain the HTML artifact entry

  @doc6 @n3
  Scenario: collectDocumentRetrieve produces a zero count without artifacts
    Given a new document project with an AsciiDoc source
    When I am executing the task 'collectDocumentRetrieve'
    Then the build should succeed
    And the composite context json file should exist
    And the composite context json should contain count zero

  @doc7 @publishing
  Scenario: generatePomFileForPluginMavenPublication produces a POM with name description license and developers
    Given a new publishable document project
    When I am executing the task 'generatePomFileForPluginMavenPublication'
    Then the build should succeed
    And the generated POM should contain the Document Gradle Plugin name
    And the generated POM should contain the Apache-2.0 license
    And the generated POM should contain the cccp-education developer
    And the generated POM should contain the scm connection

  @doc11 @book
  Scenario: The plugin registers the assembleBook task
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'assembleBook'

  @doc11 @book
  Scenario: The plugin registers the bookPipeline composite task
    Given a new document project
    When I am executing the task 'tasks'
    Then the output should contain 'bookPipeline'

  @doc11 @book
  Scenario: assembleBook merges OCR-ed AsciiDoc pages into a single book
    Given a new document project with OCR pages directory
    When I am executing the task 'assembleBook'
    Then the build should succeed
    And the assembled book file should exist
    And the assembled book should contain the book title
    And the assembled book should contain all page contents in order

  @doc11 @book
  Scenario: assembleBook embeds original photos as illustrations
    Given a new document project with OCR pages and photos directory
    When I am executing the task 'assembleBook'
    Then the build should succeed
    And the assembled book should contain photo image directives

  @doc11 @book
  Scenario: bookPipeline produces the complete book HTML PDF EPUB in one command
    Given a new document project with OCR pages directory and book formats
    When I am executing the task 'bookPipeline'
    Then the build should succeed
    And the assembled book file should exist
    And the converted HTML file should exist
    And the converted PDF file should exist
    And the converted EPUB file should exist