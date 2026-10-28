plugins {
    id("gradlenew.acceptance-tests")
}

dependencies {
    image(projects.server)
    testImplementation(projects.domain)
    testImplementation(projects.adapterJson)
    testImplementation(projects.adapterToolingApi)
}
