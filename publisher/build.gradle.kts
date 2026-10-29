plugins {
    id("gradlenew.kotlin-jvm-application")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.verification)
    implementation(projects.adapterJson)
    implementation(projects.adapterToolingApi)
    implementation(projects.adapterZip)
    implementation(projects.adapterFeeds)
    implementation(projects.versions)
    runtimeOnly(libs.slf4j.simple)
}
