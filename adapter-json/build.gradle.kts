plugins {
    id("gradlenew.kotlin-jvm-library")
}

dependencies {
    api(projects.domain)
    api(projects.verification)
    implementation(libs.kotlinx.serialization.json)
}
