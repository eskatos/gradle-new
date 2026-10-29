plugins {
    id("gradlenew.native-image-server")
}

dependencies {
    site(projects.ui)
    site(projects.publisher)
    implementation(projects.domain)
    implementation(projects.adapterJson)
    implementation(projects.adapterZip)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(libs.ktor.server.test.host)
}
