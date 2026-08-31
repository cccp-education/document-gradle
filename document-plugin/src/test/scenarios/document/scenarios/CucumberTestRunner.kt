package document.scenarios

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber.html, json:build/reports/cucumber.json"
)
// Option D (S-238): the global runner only discovers the features it OWNS.
// Its glue (`document.scenarios`) covers exactly these three; every other
// feature is owned by a dedicated runner (16th pattern) with its own glue.
// Discovering the whole features directory produced 70+ UndefinedStepException
// noise failures that masked real regressions on the `cucumberTest` gate.
@ConfigurationParameter(
    key = FEATURES_PROPERTY_NAME,
    value = "src/test/resources/features/document.feature," +
        "src/test/resources/features/release-notes.feature," +
        "src/test/resources/features/frontmatter_retranslate.feature"
)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
class CucumberTestRunner