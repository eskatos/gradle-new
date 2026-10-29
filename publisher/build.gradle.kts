plugins {
    id("gradlenew.kotlin-jvm-application")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.adapterJson)
    implementation(projects.adapterToolingApi)
    implementation(projects.adapterZip)
    runtimeOnly(libs.slf4j.simple)
}
