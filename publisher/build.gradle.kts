plugins {
    id("gradlenew.kotlin-jvm-application")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.adapterJson)
    implementation(projects.adapterToolingApi)
    runtimeOnly(libs.slf4j.simple)
}
