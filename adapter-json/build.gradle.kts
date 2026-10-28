plugins {
    id("gradlenew.kotlin-jvm-library")
}

dependencies {
    api(projects.domain)
    implementation(libs.kotlinx.serialization.json)
}
