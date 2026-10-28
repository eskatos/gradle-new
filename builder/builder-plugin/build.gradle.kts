plugins {
    `java-library`
}

group = "gradlenew.builder"
version = "0.1.0-SNAPSHOT"

dependencies {
    api(project(":builder-api"))
    implementation(gradleApi())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
