plugins {
    id("gradlenew.kotlin-common-library")
}

dependencies {
    commonMainApi(projects.domain)
    commonMainApi(projects.substitution)
}
