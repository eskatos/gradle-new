package gradlenew.domain.generation

import gradlenew.domain.model.BuildType
import gradlenew.domain.model.Choice
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.Language
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter

val gradle982Model = InitModel(
    gradleVersion = "9.8.2",
    componentTypes = listOf(ComponentType("application", "Application", listOf("app", "list", "utilities")), ComponentType("library", "Library", listOf("lib")), ComponentType("gradle-plugin", "Gradle plugin", listOf("plugin")), ComponentType("basic", "Basic (build structure only)", emptyList())),
    languages = listOf(Language("java", "Java"), Language("kotlin", "Kotlin"), Language("groovy", "Groovy"), Language("scala", "Scala"), Language("cpp", "C++"), Language("swift", "Swift")),
    dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts"), Dsl("groovy", "Groovy", ".gradle")),
    modularizationOptions = listOf(ModularizationOption("single-project", "Single application project"), ModularizationOption("with-library-projects", "Application and library project")),
    testFrameworks = listOf(TestFramework("junit", "JUnit 4"), TestFramework("testng", "TestNG"), TestFramework("spock", "Spock"), TestFramework("kotlintest", "kotlin.test"), TestFramework("scalatest", "ScalaTest"), TestFramework("junit-jupiter", "JUnit Jupiter"), TestFramework("xctest", "XCTest"), TestFramework("cpptest", "C++ executable")),
    buildTypes = listOf(
        BuildType("basic", "basic", null, Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", emptyList(), null)), false, false, true, null),
        BuildType("cpp-application", "application", "cpp", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("cpptest"), "cpptest")), false, false, true, null),
        BuildType("cpp-library", "library", "cpp", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("cpptest"), "cpptest")), false, false, true, null),
        BuildType("groovy-application", "application", "groovy", Choice(listOf("kotlin", "groovy"), "groovy"), Choice(listOf("single-project", "with-library-projects"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("spock"), "spock"), TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter")), true, true, true, null),
        BuildType("groovy-gradle-plugin", "gradle-plugin", "groovy", Choice(listOf("kotlin", "groovy"), "groovy"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("spock"), "spock")), true, false, true, null),
        BuildType("groovy-library", "library", "groovy", Choice(listOf("kotlin", "groovy"), "groovy"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("spock"), "spock")), true, true, true, null),
        BuildType("java-application", "application", "java", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project", "with-library-projects"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("junit", "testng", "spock", "junit-jupiter"), "junit-jupiter"), TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter")), true, true, true, null),
        BuildType("java-gradle-plugin", "gradle-plugin", "java", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("junit-jupiter"), "junit-jupiter")), true, false, true, null),
        BuildType("java-library", "library", "java", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("junit", "testng", "spock", "junit-jupiter"), "junit-jupiter")), true, true, true, null),
        BuildType("kotlin-application", "application", "kotlin", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project", "with-library-projects"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("kotlintest", "junit-jupiter"), "kotlintest"), TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter")), true, true, true, null),
        BuildType("kotlin-gradle-plugin", "gradle-plugin", "kotlin", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("kotlintest"), "kotlintest")), true, false, true, null),
        BuildType("kotlin-library", "library", "kotlin", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("kotlintest", "junit-jupiter"), "kotlintest")), true, true, true, null),
        BuildType("scala-application", "application", "scala", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project", "with-library-projects"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("scalatest"), "scalatest"), TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter")), true, true, true, null),
        BuildType("scala-library", "library", "scala", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("scalatest"), "scalatest")), true, true, true, null),
        BuildType("swift-application", "application", "swift", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("xctest"), "xctest")), false, false, true, null),
        BuildType("swift-library", "library", "swift", Choice(listOf("kotlin", "groovy"), "kotlin"), Choice(listOf("single-project"), "single-project"), listOf(TestFrameworkChoice("single-project", listOf("xctest"), "xctest")), false, false, true, null),
    ),
    javaVersion = JavaVersionParameter(7, 21),
    packageName = TextParameter("^[A-Za-z_\$][A-Za-z0-9_\$]*(\\.[A-Za-z_\$][A-Za-z0-9_\$]*)*\$", "org.example"),
    projectName = TextParameter(null, null),
)
