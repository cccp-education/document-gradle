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
