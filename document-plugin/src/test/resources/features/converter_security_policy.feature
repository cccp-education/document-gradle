Feature: Converter security policy (DOC-CR5)

  As a document-gradle user
  I want the AsciidoctorJ SafeMode x includeGuard combination to be coherent
  So that enabling an include guard does not leave other AsciidoctorJ filesystem access unrestricted

  @converter-security-policy
  Scenario: STRICT include guard with UNSAFE safeMode rejects the conversion
    Given a document gradle project with safeMode "UNSAFE" and includeGuard "STRICT"
    When the plugin is applied and convertDocumentToHtml runs and fails
    Then the build fails with security policy message "Security policy (STRICT)"

  @converter-security-policy
  Scenario: LENIENT include guard with UNSAFE safeMode warns but converts
    Given a document gradle project with safeMode "UNSAFE" and includeGuard "LENIENT"
    When the plugin is applied and convertDocumentToHtml runs successfully
    Then the build succeeds with security policy warning "Security policy (LENIENT)"

  @converter-security-policy
  Scenario: SERVER safeMode with STRICT include guard is coherent
    Given a document gradle project with safeMode "SERVER" and includeGuard "STRICT"
    When the plugin is applied and convertDocumentToHtml runs successfully
    Then the build succeeds without security policy message
