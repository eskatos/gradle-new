plugins {
    `java-library`
}

group = "gradlenew.builder"
version = "0.1.0-SNAPSHOT"

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}
