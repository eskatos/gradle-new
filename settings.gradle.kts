pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "gradle-new"

include(
    "substitution",
    "domain",
    "versions",
    "verification",
    "adapter-json",
    "adapter-zip",
    "adapter-feeds",
    "adapter-tooling-api",
    "publisher",
    "acceptance",
)
