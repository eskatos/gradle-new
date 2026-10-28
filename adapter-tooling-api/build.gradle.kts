plugins {
    id("gradlenew.kotlin-jvm-library")
}

dependencies {
    implementation(libs.gradle.tooling.api)
    testRuntimeOnly(libs.slf4j.simple)
}
