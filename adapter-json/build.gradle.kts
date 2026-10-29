plugins {
    id("gradlenew.kotlin-jvm-library")
}

dependencies {
    api(projects.domain)
    api(projects.verification)
    api(projects.versions)
    implementation(libs.kotlinx.serialization.json)
}
