plugins {
    id("gradlenew.acceptance-tests")
}

dependencies {
    image(projects.server)
    publisher(projects.publisher)
    testImplementation(projects.domain)
    testImplementation(projects.verification)
    testImplementation(projects.adapterJson)
    testImplementation(projects.versions)
    testImplementation(projects.adapterToolingApi)
}
