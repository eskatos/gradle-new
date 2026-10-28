plugins {
    id("gradlenew.acceptance-tests")
}

dependencies {
    image(projects.server)
    site(projects.ui)
}
