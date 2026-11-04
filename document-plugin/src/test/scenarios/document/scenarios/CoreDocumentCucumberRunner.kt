package document.scenarios

import io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * DOC-TEST-PERF US-4 — restores the coverage of `document.feature` and
 * `release-notes.feature`.
 *
 * Both features were **orphans**: they carry `@document` / `@doc8`, tags no
 * focused runner filters on, so their scenarios were executed by no task at
 * all (the aggregate `cucumberTest` also only runs the tag-scoped suites).
 * This runner selects exactly those two tags and reuses the shared
 * `document.scenarios` glue.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.scenarios")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@document or @doc8")
class CoreDocumentCucumberRunner
