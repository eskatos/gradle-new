plugins {
    `java-gradle-plugin`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlinx.serialization.gradle.plugin)
    implementation(libs.graalvm.buildtools.gradle.plugin)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("kotlinCommonLibrary") {
            id = "gradlenew.kotlin-common-library"
            implementationClass = "gradlenew.buildlogic.KotlinCommonLibraryPlugin"
        }
        create("kotlinJvmLibrary") {
            id = "gradlenew.kotlin-jvm-library"
            implementationClass = "gradlenew.buildlogic.KotlinJvmLibraryPlugin"
        }
        create("kotlinJvmApplication") {
            id = "gradlenew.kotlin-jvm-application"
            implementationClass = "gradlenew.buildlogic.KotlinJvmApplicationPlugin"
        }
        create("nativeImageServer") {
            id = "gradlenew.native-image-server"
            implementationClass = "gradlenew.buildlogic.NativeImageServerPlugin"
        }
        create("staticSite") {
            id = "gradlenew.static-site"
            implementationClass = "gradlenew.buildlogic.StaticSitePlugin"
        }
        create("acceptanceTests") {
            id = "gradlenew.acceptance-tests"
            implementationClass = "gradlenew.buildlogic.AcceptanceTestsPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
