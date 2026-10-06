package document.translation.resilience

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/translation_resilience.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.translation.resilience")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features/translation_resilience.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@translation-resilience")
class TranslationResilienceCucumberRunner
