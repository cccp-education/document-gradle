@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
    id("com.gradleup.nmcp.settings").version("1.5.0")
}

val globalProps = java.util.Properties().also {
    val globalFile = file(System.getProperty("user.home") + "/.gradle/gradle.properties")
    if (globalFile.exists()) it.load(globalFile.inputStream())
}

// DOC-CI-ISOLATION (D2) — credentials are OPTIONAL at configuration time.
// A GitHub runner has no ~/.gradle/gradle.properties; hard-failing here
// (`?: error(...)`) kills every invocation — `./gradlew build` included —
// before a single test runs, neutralising "Local = Build, Tests = CI".
// bakery BKY-CI-ISOLATION (S-243) proved the `?: ""` fallback: an isolated
// checkout configures and tests fine; only a real
// publishAggregationToCentralPortal fails later, without credentials.
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername") ?: ""
        password = globalProps.getProperty("ossrhPassword") ?: ""
        publishingType = "AUTOMATIC"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

// ── MEM-CAT-ROLLOUT-1 — Catalog workspace published (MEMPHIS): single pin per borough (D4) ──
// education.cccp:workspace-catalog:0.0.58 — cross-borough source of truth for plugin versions.
dependencyResolutionManagement {
    versionCatalogs {
        create("ws") {
            from("education.cccp:workspace-catalog:0.0.58")
        }
    }
}

rootProject.name = "document-plugin"