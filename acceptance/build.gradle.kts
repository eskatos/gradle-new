plugins {
    id("gradlenew.acceptance-tests")
}

dependencies {
    image(projects.server)
    site(projects.ui)
    testImplementation(projects.domain)
    testImplementation(projects.adapterJson)
    testImplementation(projects.adapterToolingApi)
}
