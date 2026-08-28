package document.booktree

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber runner for the `@book-tree` scenarios
 * (DOC-BOOK-DOMAIN-5 — structured book assembly BDD).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.booktree")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@book-tree")
class BookTreeCucumberRunner
