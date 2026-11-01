package document.translation

import io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/frontmatter_retranslate.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.scenarios, document.translation")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features/frontmatter_retranslate.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@frontmatter-retranslate")
class FrontmatterRetranslateCucumberRunner
