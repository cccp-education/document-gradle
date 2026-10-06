@translation-resilience
Feature: Delta translation resilience (an LLM failure is never frozen)

  The delta preserves a TRANSLATED block whose source hash is unchanged, so a
  failed LLM call promoted to TRANSLATED would freeze the silent
  source-language fallback forever — the batch reports 0 errors without ever
  converging. A failing block must be stored PENDING and re-attempted.

  Scenario: A failed block is stored PENDING and re-attempted until it converges
    Given a resilience fixture whose LLM fails on "Second paragraph."
    And a resilient source article with a failing paragraph "Second paragraph." and a healthy paragraph "First paragraph."
    When the resilient delta translation runs
    Then the resilient block of "Second paragraph." is pending
    And the resilient block of "First paragraph." is translated
    And the resilient target still contains the silent fallback "Second paragraph."
    When the resilient LLM has recovered
    And the resilient delta translation runs
    Then the resilient block of "Second paragraph." is translated
    And the resilient target contains the translation "Second paragraph. [EN]"

  Scenario: A fully successful translation marks every block TRANSLATED
    Given a resilience fixture whose LLM always succeeds
    And a resilient source article with a failing paragraph "Second paragraph." and a healthy paragraph "First paragraph."
    When the resilient delta translation runs
    Then the resilient block of "Second paragraph." is translated
    And the resilient block of "First paragraph." is translated
