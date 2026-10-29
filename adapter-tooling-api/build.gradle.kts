plugins {
    id("gradlenew.kotlin-jvm-library")
}

jvmLibrary {
    testsUseOfferedGradleVersions = true
}

dependencies {
    api(projects.domain)
    api(projects.substitution)
    implementation("gradlenew.builder:builder-api")
    implementation(libs.gradle.tooling.api)
    testRuntimeOnly(libs.slf4j.simple)
}
