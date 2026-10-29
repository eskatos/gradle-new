plugins {
    id("gradlenew.acceptance-tests")
}

dependencies {
    image(projects.server)
    publisher(projects.publisher)
    testImplementation(projects.domain)
    testImplementation(projects.adapterJson)
    testImplementation(projects.adapterToolingApi)
}
