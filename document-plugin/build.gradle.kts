import build.CucumberTaskSpec

plugins {
    id("education.cccp.build.gradle-plugin") version "0.0.4"
    id("education.cccp.build.publishing") version "0.0.4"
    id("education.cccp.build.functional-test") version "0.0.4"
    id("education.cccp.build.cucumber") version "0.0.4"
}

group = "education.cccp"
version = "0.0.13"

dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.26"))

    implementation(kotlin("stdlib-jdk8"))

    // DOC-METADATA-VALIDATION — Jackson Kotlin module for data-class deserialization
    // (DocumentValidationReport.fromJson). Version constrained by workspace-bom.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // AsciidoctorJ — implementation directe (boundary Codex : pas de compileOnly codex)
    implementation("org.asciidoctor:asciidoctorj")
    implementation("org.asciidoctor:asciidoctorj-diagram")
    implementation("org.asciidoctor:asciidoctorj-diagram-plantuml")
    // Session 233 (option C) — PDF/EPUB backend gems. The core asciidoctorj jar only
    // bundles the `asciidoctor` gem; the `pdf`/`epub3` backends live in their own
    // gem jars (versioned by the catalogue, present in repositories). runtimeOnly:
    // the converter gems are discovered from the classpath, no compile-time API used.
    runtimeOnly("org.asciidoctor:asciidoctorj-pdf:2.3.23")
    runtimeOnly("org.asciidoctor:asciidoctorj-epub3:2.2.0")

    // koog — orchestrateur de graphe agentique (EPIC L : koog orchestre, langchain4j execute)
    implementation("ai.koog:koog-agents")

    // langchain4j — execution LLM (Ollama local, port 11437-11465)
    implementation("dev.langchain4j:langchain4j-ollama")

    // LLM bridge — partage avec planner-gradle (compileOnly, evite duplication)
    compileOnly("education.cccp:planner-plugin:0.0.1")

    // PlantUML — composition (contenant→contenu), implementation pour validation syntaxique post-traduction
    implementation("education.cccp:plantuml-plugin")

    // N0 contracts — i18n (internationalisation documents)
    // + opencode-session (traçabilité release notes, vision MEM-4 — non implémenté, gardé pour roadmap)
    // + pipeline-contracts (release notes generator, MEM-2 DOC-8)
    implementation("education.cccp:i18n-contracts")
    implementation("education.cccp:opencode-session-contracts")
    implementation("education.cccp:pipeline-contracts")

    // Coroutines — ContentTranslationService parallel translation
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    // SLF4J — logging in ContentTranslationService
    implementation("org.slf4j:slf4j-api")

    // Tests unitaires
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("org.mockito:mockito-junit-jupiter")

    // Cucumber BDD
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("io.cucumber:cucumber-picocontainer")
    testImplementation("org.junit.platform:junit-platform-suite")
}


// Forward the dogfooding publish flag to the test JVM so the FPA-BOOK-4
// integration test can copy generated artifacts into office/metiers/FPA.
tasks.named<Test>("test") {
    systemProperty("fpa.book.publish", System.getProperty("fpa.book.publish") ?: "false")
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
        CucumberTaskSpec(
            name = "bookValidationCucumberTest",
            features = listOf("src/test/resources/features/book_validation.feature"),
            tags = listOf("@book-validation"),
            runnerClass = "document.bookvalidation.BookValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "converterSafeModeCucumberTest",
            features = listOf("src/test/resources/features/converter_safe_mode.feature"),
            tags = listOf("@converter-safe-mode"),
            runnerClass = "document.convertersafemode.ConverterSafeModeCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "includeGuardCucumberTest",
            features = listOf("src/test/resources/features/converter_include_guard.feature"),
            tags = listOf("@converter-include-guard"),
            runnerClass = "document.includeguard.IncludeGuardCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "securityPolicyCucumberTest",
            features = listOf("src/test/resources/features/converter_security_policy.feature"),
            tags = listOf("@converter-security-policy"),
            runnerClass = "document.securitypolicy.SecurityPolicyCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "xrefValidationCucumberTest",
            features = listOf("src/test/resources/features/xref_validation.feature"),
            tags = listOf("@xref-validation"),
            runnerClass = "document.xrefvalidation.XrefValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "documentValidationCucumberTest",
            features = listOf("src/test/resources/features/document_validation.feature"),
            tags = listOf("@document-validation"),
            runnerClass = "document.validation.DocumentValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "htmlLinkLintCucumberTest",
            features = listOf("src/test/resources/features/html_link_lint.feature"),
            tags = listOf("@html-link-lint"),
            runnerClass = "document.htmllint.HtmlLinkLintCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "metadataValidationCucumberTest",
            features = listOf("src/test/resources/features/metadata_validation.feature"),
            tags = listOf("@metadata-validation"),
            runnerClass = "document.metadatavalidation.MetadataValidationCucumberRunner",
        ),
        CucumberTaskSpec(
            name = "n3PipelineCucumberTest",
            features = listOf("src/test/resources/features/n3_pipeline.feature"),
            tags = listOf("@n3-pipeline"),
            runnerClass = "document.n3pipeline.N3PipelineCucumberRunner",
        ),
    )
}

gradlePlugin {
    website.set("https://github.com/cccp-education/document-gradle/")
    vcsUrl.set("https://github.com/cccp-education/document-gradle.git")

    plugins {
        create("document") {
            id = "education.cccp.document"
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
    repositories.mavenCentral()
}
