plugins {
    id("gradlenew.kotlin-jvm-library")
}

dependencies {
    api(projects.domain)
    implementation("gradlenew.builder:builder-api")
    implementation(libs.gradle.tooling.api)
    testRuntimeOnly(libs.slf4j.simple)
}
