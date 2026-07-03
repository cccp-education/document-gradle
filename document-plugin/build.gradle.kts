plugins {
    id("education.cccp.build.gradle-plugin") version "0.0.1"
    id("education.cccp.build.publishing") version "0.0.1"
    id("education.cccp.build.functional-test") version "0.0.1"
    id("education.cccp.build.cucumber") version "0.0.1"
}

group = "education.cccp"
version = libs.plugins.document.get().version

dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.4"))

    implementation(kotlin("stdlib-jdk8"))

    // AsciidoctorJ — implementation directe (boundary Codex : pas de compileOnly codex)
    implementation(libs.bundles.asciidoctor)

    // koog — orchestrateur de graphe agentique (EPIC L : koog orchestre, langchain4j execute)
    implementation(libs.bundles.koog)

    // langchain4j — execution LLM (Ollama local, port 11437-11465)
    implementation(libs.langchain4j.ollama)

    // LLM bridge — partage avec planner-gradle (compileOnly, evite duplication)
    compileOnly(libs.planner.plugin)

    // PlantUML — composition (contenant→contenu), compileOnly legitime
    compileOnly(libs.plantuml.plugin)

    // N0 contracts — i18n (internationalisation documents) + opencode-session (traçabilité release notes)
    implementation(libs.i18n.contracts)
    implementation(libs.opencode.session.contracts)

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

publishing {
    repositories {
        mavenCentral()
    }
}