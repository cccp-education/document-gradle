import build.CucumberTaskSpec

plugins {
    id("education.cccp.build.gradle-plugin") version "0.0.2"
    id("education.cccp.build.publishing") version "0.0.2"
    id("education.cccp.build.functional-test") version "0.0.2"
    id("education.cccp.build.cucumber") version "0.0.2"
}

group = "education.cccp"
version = libs.plugins.document.get().version

dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.11"))

    implementation(kotlin("stdlib-jdk8"))

    // AsciidoctorJ — implementation directe (boundary Codex : pas de compileOnly codex)
    implementation(libs.bundles.asciidoctor)

    // koog — orchestrateur de graphe agentique (EPIC L : koog orchestre, langchain4j execute)
    implementation(libs.bundles.koog)

    // langchain4j — execution LLM (Ollama local, port 11437-11465)
    implementation(libs.langchain4j.ollama)

    // LLM bridge — partage avec planner-gradle (compileOnly, evite duplication)
    compileOnly(libs.planner.plugin)

    // PlantUML — composition (contenant→contenu), implementation pour validation syntaxique post-traduction
    implementation(libs.plantuml.plugin)

    // N0 contracts — i18n (internationalisation documents)
    // + opencode-session (traçabilité release notes, vision MEM-4 — non implémenté, gardé pour roadmap)
    // + pipeline-contracts (release notes generator, MEM-2 DOC-8)
    implementation(libs.i18n.contracts)
    implementation(libs.opencode.session.contracts)
    implementation(libs.pipeline.contracts)

    // Coroutines — ContentTranslationService parallel translation
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)

    // SLF4J — logging in ContentTranslationService
    implementation(libs.slf4j.api)

    // Tests unitaires
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.junit.jupiter)

    // Cucumber BDD
    testImplementation(libs.bundles.cucumber)
}

cucumberConventions {
    additionalTasks = listOf(
        CucumberTaskSpec(
            name = "tableTranslationCucumberTest",
            features = listOf("src/test/resources/features/table_translation.feature"),
            tags = listOf("@table"),
            runnerClass = "document.translation.TableTranslationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "tableValidationCucumberTest",
            features = listOf("src/test/resources/features/table_validation.feature"),
            tags = listOf("@table-validation"),
            runnerClass = "document.translation.validation.TableValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "plantumlValidationCucumberTest",
            features = listOf("src/test/resources/features/plantuml_validation.feature"),
            tags = listOf("@plantuml-validation"),
            runnerClass = "document.plantumlvalidation.PlantUmlValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "frontmatterRetranslateCucumberTest",
            features = listOf("src/test/resources/features/frontmatter_retranslate.feature"),
            tags = listOf("@frontmatter-retranslate"),
            runnerClass = "document.translation.FrontmatterRetranslateCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "crossBoroughCucumberTest",
            features = listOf("src/test/resources/features/cross_borough_book.feature"),
            tags = listOf("@cross-borough"),
            runnerClass = "document.crossborough.CrossBoroughCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "bookTreeCucumberTest",
            features = listOf("src/test/resources/features/book_tree.feature"),
            tags = listOf("@book-tree"),
            runnerClass = "document.booktree.BookTreeCucumberRunner",
        ),
    )
}

gradlePlugin {
    website.set("https://github.com/cccp-education/document-gradle/")
    vcsUrl.set("https://github.com/cccp-education/document-gradle.git")

    plugins {
        create("document") {
            id = libs.plugins.document.get().pluginId
            implementationClass = "document.DocumentPlugin"
            displayName = "Document Plugin"
            description = "Gradle plugin for AsciiDoc document creation and multi-format publication (HTML, PDF, EPUB, DocBook, ManPage) via AsciidoctorJ."
            tags.set(listOf("asciidoc", "documentation", "pdf", "html", "epub", "asciidoctorj", "publishing"))
        }
    }
}

publishingConventions {
    publicationType = "PLUGIN"
}

// RELOCATION : garde comme echappatoire future.
// Activable avec -Prem relocationGroup="<futur-namespace>"

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("Document Gradle Plugin")
            description.set("Gradle plugin for AsciiDoc document creation and multi-format publication (HTML, PDF, EPUB, DocBook, ManPage) via AsciidoctorJ.")
        }
    }
    repositories {
        mavenCentral()
    }
}