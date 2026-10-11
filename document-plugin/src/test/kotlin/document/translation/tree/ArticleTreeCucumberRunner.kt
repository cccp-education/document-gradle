package document.translation.tree

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** GBL-010 G-2 — the article AST tree (article → document → plantuml leaves). */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/article_tree.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "document.translation.tree")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features/article_tree.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@article-tree")
class ArticleTreeCucumberRunner
