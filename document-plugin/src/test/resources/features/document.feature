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

  @doc5 @conversion @docbook-advanced
  Scenario: convertDocumentToDocBook renders AsciiDoc tables and code blocks into DocBook elements
    Given a new document project with an AsciiDoc source containing a table and a code block
    When I am executing the task 'convertDocumentToDocBook'
    Then the build should succeed
    And the converted DocBook file should exist
    And the converted DocBook should render the table as a DocBook table element
    And the converted DocBook should render the code block as a programlisting element

  @doc5 @conversion @manpage-advanced
  Scenario: convertDocumentToManPage renders formatted options in bold troff directives
    Given a new document project with an AsciiDoc manpage source with formatted options
    When I am executing the task 'convertDocumentToManPage'
    Then the build should succeed
    And the converted ManPage file should exist
    And the converted ManPage should render the options in bold troff directives

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

  @doc12 @dsl
  Scenario: The document DSL accepts an enrich block with plantuml images and passthrough flags
    Given a new document project with a unified DSL enrich block
    When I am executing the task 'tasks'
    Then the build should succeed

  @doc12 @dsl
  Scenario: The document DSL accepts an outputs block with html pdf and epub flags
    Given a new document project with a unified DSL outputs block
    When I am executing the task 'tasks'
    Then the build should succeed

  @doc12 @dsl
  Scenario: The document DSL accepts a metadata block with title author and language
    Given a new document project with a unified DSL metadata block
    When I am executing the task 'tasks'
    Then the build should succeed

  @doc12 @dsl
  Scenario: The document DSL accepts the full unified document block
    Given a new document project with a full unified DSL document block
    When I am executing the task 'tasks'
    Then the build should succeed

  @doc12 @serialization
  Scenario: The serializeDocumentConfig task produces a document-config json file
    Given a new document project with a full unified DSL document block
    When I am executing the task 'serializeDocumentConfig'
    Then the build should succeed
    And the document config json file should exist
    And the document config json should contain the source filename
    And the document config json should contain the enrich flags
    And the document config json should contain the outputs flags
    And the document config json should contain the metadata title

  @doc12 @serialization
  Scenario: The document config json contains default values when no DSL block is configured
    Given a new document project
    When I am executing the task 'serializeDocumentConfig'
    Then the build should succeed
    And the document config json file should exist
    And the document config json should contain default enrich flags all false
    And the document config json should contain default outputs html only
    And the document config json should contain default metadata language fr

  @doc12 @serialization @book
  Scenario: The serializeDocumentConfig task serialises the book block into document-config json
    Given a new document project with a unified DSL book block with photos
    When I am executing the task 'serializeDocumentConfig'
    Then the build should succeed
    And the document config json file should exist
    And the document config json should contain the book block with title and author

  @doc12 @round-trip
  Scenario: serializeDocumentConfig is idempotent — two consecutive runs produce byte-identical json
    Given a new document project with a full unified DSL document block
    When I am executing the task 'serializeDocumentConfig' twice in a row
    Then the build should succeed
    And the two document config json outputs must be byte-identical

  @doc12 @round-trip @deserialize
  Scenario: deserializeDocumentConfig round-trips the serialized config producing byte-identical json
    Given a new document project with a full unified DSL document block
    When I am executing the serializeDocumentConfig then deserializeDocumentConfig tasks
    Then the build should succeed
    And the round-tripped document config json file should exist
    And the round-tripped document config json should be byte-identical to the source

  @doc11 @book @n3 @cross-borough
  Scenario: bookPipeline produces a composite-context json consumable by runner-gradle N3 with book artifacts
    Given a new document project with OCR pages and photos directory and a source pointing to the assembled book
    When I am executing the task 'bookPipeline'
    Then the build should succeed
    And the assembled book file should exist
    And the converted HTML file should exist
    And the converted PDF file should exist
    And the converted EPUB file should exist
    And the composite context json file should exist
    And the composite context json should index the html pdf and epub book artifacts

  @doc12 @dsl @book
  Scenario: The document DSL accepts a book block with pagesDir title and author
    Given a new document project with a unified DSL book block
    When I am executing the task 'assembleBook'
    Then the build should succeed
    And the assembled book file should exist
    And the assembled book should contain the DSL book title
    And the assembled book should contain the DSL book author
    And the assembled book should contain all page contents in order

  @doc12 @dsl @book
  Scenario: The document DSL book block accepts a photosDir and embeds photo image directives
    Given a new document project with a unified DSL book block with photos
    When I am executing the task 'assembleBook'
    Then the build should succeed
    And the assembled book file should exist
    And the assembled book should contain photo image directives

  @doc12 @dsl @book
  Scenario: The document DSL book block falls back to default title and author when unset
    Given a new document project with a unified DSL book block without title and author
    When I am executing the task 'assembleBook'
    Then the build should succeed
    And the assembled book file should exist
    And the assembled book should contain the default book title
    And the assembled book should contain the default book author

  @doc9 @enrichment @images
  Scenario: convertDocumentToHtml renders image directives into img tags in the final HTML
    Given a new document project with an AsciiDoc source containing an image directive
    When I am executing the task 'convertDocumentToHtml'
    Then the build should succeed
    And the converted HTML file should exist
    And the converted HTML should render an img tag for the image directive

  @doc9 @enrichment @images @epub
  Scenario: convertDocumentToEpub embeds image directives as zip entries in the final EPUB
    Given a new document project with an AsciiDoc source containing an image directive
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should embed the image directive as a zip entry

  @doc5 @conversion @epub-advanced
  Scenario: convertDocumentToEpub renders AsciiDoc tables and code blocks into XHTML elements
    Given a new document project with an AsciiDoc source containing a table and a code block
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should render the table as a XHTML table element
    And the converted EPUB should render the code block as a pre code element

  @doc5 @conversion @epub-advanced
  Scenario: convertDocumentToEpub renders AsciiDoc lists into XHTML ul and ol elements
    Given a new document project with an AsciiDoc source containing ordered and unordered lists
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should render the unordered list as a ul element
    And the converted EPUB should render the ordered list as an ol element

  @doc5 @conversion @epub-advanced
  Scenario: convertDocumentToEpub renders AsciiDoc admonitions into XHTML aside elements
    Given a new document project with an AsciiDoc source containing admonitions
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should render the admonitions as aside elements

  @doc5 @conversion @epub-advanced
  Scenario: convertDocumentToEpub renders AsciiDoc sidebars into XHTML aside sidebar elements
    Given a new document project with an AsciiDoc source containing a sidebar block
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should render the sidebar as an aside sidebar element

  @doc5 @conversion @epub-advanced
  Scenario: convertDocumentToEpub renders AsciiDoc bibliography into XHTML bibliography element
    Given a new document project with an AsciiDoc source containing a bibliography block
    When I am executing the task 'convertDocumentToEpub'
    Then the build should succeed
    And the converted EPUB file should exist
    And the converted EPUB should render the bibliography as a bibliography element

  @doc13 @template
  Scenario: applyDocumentTemplate substitutes variables from DSL
    Given a new document project with a template DSL
    When I am executing the task 'applyDocumentTemplate'
    Then the build should succeed
    And the generated document should contain '= My Doc'
    And the generated document should contain ':author: Jane'

  @doc13 @template
  Scenario: applyDocumentTemplate fails on missing variable by default
    Given a new document project with a template DSL and a missing variable
    When I am executing the task 'applyDocumentTemplate'
    Then the build should fail
    And the output should contain 'missing'

  @doc13 @template
  Scenario: applyDocumentTemplate keeps placeholder when failOnMissingVariable is false
    Given a new document project with a template DSL and failOnMissingVariable set to false
    When I am executing the task 'applyDocumentTemplate'
    Then the build should succeed
    And the generated document should contain '{{body}}'

  @doc14 @batch
  Scenario: batchConvertDocuments converts all adoc files in a directory
    Given a new document project with a batch DSL
    When I am executing the task 'batchConvertDocuments'
    Then the build should succeed
    And the output should contain 'batchConvertDocuments'

  @doc14 @batch
  Scenario: batchConvertDocuments task is listed in document group
    Given a new document project
    When I am executing the task 'tasks' with group 'document'
    Then the output should contain 'batchConvertDocuments'

  @doc-translate @translation
  Scenario: translateDocument task is registered
    Given a new document project
    When I am executing the task 'tasks' with group 'document'
    Then the output should contain 'translateDocument'

  @doc-translate @translation
  Scenario: translateDocument translates FR to EN via fake LLM
    Given a new document project with translation DSL
    When I am executing the task 'translateDocument'
    Then the build should succeed
    And the translated document should contain 'Bonjour le monde [EN]'
    And the translated document should contain 'Introduction [EN]'

  @doc-translate @translation
  Scenario: translateDocument preserves source code blocks
    Given a new document project with translation DSL and source code
    When I am executing the task 'translateDocument'
    Then the build should succeed
    And the translated document should contain 'public class Hello {}'
    And the translated document should contain 'Some text after code. [EN]'

  @doc-translate @translation @jbake-native
  Scenario: translateDocument preserves cheroliv.com JBake native article structure
    Given a new document project with translation DSL and a JBake native article
    When I am executing the task 'translateDocument'
    Then the build should succeed
    And the translated document should contain '@CherOliv'
    And the translated document should contain ':jbake-type: post'
    And the translated document should contain ':jbake-status: published'
    And the translated document should contain 'plugins {'
    And the translated document should not contain '~~~~~~'