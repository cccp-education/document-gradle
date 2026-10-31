@preserve
Feature: MEM-I18N-PRESERVE — what a translation must never touch

  The shared N0 catalog (`contracts.i18n.PreserveRule`) defines the AsciiDoc
  markers a translation provider must never receive: a translated `toc::[]`
  broke 142 published files. Document classifies a whole block before sending it
  to the model, so the catalog decides what is markup and what is prose.

  Scenario: A pure asciidoc macro is never translatable
    Given a preserve block "toc::[]"
    When the preserve classifier classifies the block
    Then the block is not translatable

  Scenario: A pure inline code span is never translatable
    Given a preserve block "`gradle build`"
    When the preserve classifier classifies the block
    Then the block is not translatable

  Scenario: An anchor alone is never translatable
    Given a preserve block "[[page-10]]"
    When the preserve classifier classifies the block
    Then the block is not translatable

  Scenario: Prose citing a macro stays translatable
    Given a preserve block "Voir toc::[] pour la table des matières"
    When the preserve classifier classifies the block
    Then the block is translatable

  Scenario: Ordinary prose stays translatable
    Given a preserve block "Bonjour le monde"
    When the preserve classifier classifies the block
    Then the block is translatable
