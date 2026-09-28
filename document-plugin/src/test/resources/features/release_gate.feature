@release-gate
Feature: Release gate — publishing only from a tag
  As a maintainer of document-gradle
  I want the CI to publish to Maven Central only from a version tag
  So that a branch push can never publish, and a release always has a GPG key

  Scenario: A complete release workflow is compliant
    Given release-gate a complete release workflow
    When release-gate the workflow is audited
    Then release-gate the workflow is compliant

  Scenario: Publishing without a tag gate is rejected
    Given release-gate a release workflow missing "the tag gate"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant

  Scenario: Publishing without a needs on the test job is rejected
    Given release-gate a release workflow missing "the needs on the test job"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant

  Scenario: Publishing without a GPG key import is rejected
    Given release-gate a release workflow missing "the GPG key import"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant

  Scenario: Publishing without the CCCP_PUBLISH signal is rejected
    Given release-gate a release workflow missing "the CCCP_PUBLISH signal"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant

  Scenario: Publishing without the OSSRH secrets is rejected
    Given release-gate a release workflow missing "the OSSRH secrets"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant

  Scenario: A workflow without any publish task is rejected
    Given release-gate a release workflow missing "the publish task"
    When release-gate the workflow is audited
    Then release-gate the workflow is non compliant
