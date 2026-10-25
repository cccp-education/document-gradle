@article-tree
Feature: Article AST tree — a document and its plantuml leaves

  The AsciiDoc pivot is a flat block list; the article tree groups it under its
  headings and surfaces the leaves the plantuml borough owns (GBL-010 G-2).

  Scenario: Headings open nested sections
    Given an article with a level 1 heading "Intro" holding a paragraph
    And a level 2 heading "Details" holding a paragraph
    When the article tree is built
    Then the article tree has one top-level section "Intro"
    And the section "Intro" owns one nested section "Details"

  Scenario: A plantuml block is a plantuml leaf
    Given an article with a plantuml block
    When the article tree is audited
    Then the article tree counts 1 plantuml leaves
    And the article has diagrams

  Scenario: A non-plantuml source block is a document leaf
    Given an article with a java source block
    When the article tree is audited
    Then the article tree counts 0 plantuml leaves

  Scenario: An empty plantuml block is missing
    Given an empty plantuml block
    When the article tree is audited
    Then the article tree counts 1 plantuml leaves with status MISSING
    And the article tree reports the plantuml leaf status MISSING

  Scenario: A plantuml block with translatable labels is pending
    Given a plantuml block with translatable labels
    When the article tree is audited
    Then the article tree counts 1 plantuml leaves with status PENDING
    And the report renders the plantuml status PENDING

  Scenario: A plantuml block with no translatable labels is translated
    Given an article with a plantuml block
    When the article tree is audited
    Then the article tree counts 1 plantuml leaves with status TRANSLATED
    And the article tree reports the plantuml leaf status TRANSLATED

  Scenario: An invalid plantuml block is broken
    Given an invalid plantuml block
    When the article tree is audited
    Then the article tree counts 1 plantuml leaves with status BROKEN
    And the article tree reports the plantuml leaf status BROKEN
