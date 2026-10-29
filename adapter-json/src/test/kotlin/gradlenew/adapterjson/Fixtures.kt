package gradlenew.adapterjson

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

val sampleModel = InitModel(
    gradleVersion = "9.8.2",
    componentTypes = listOf(
        ComponentType("application", "Application", listOf("app", "list", "utilities")),
        ComponentType("basic", "Basic (build structure only)", emptyList()),
    ),
    languages = listOf(Language("java", "Java")),
    dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts"), Dsl("groovy", "Groovy", ".gradle")),
    modularizationOptions = listOf(
        ModularizationOption("single-project", "Single application project"),
        ModularizationOption("with-library-projects", "Application and library project"),
    ),
    testFrameworks = listOf(TestFramework("junit-jupiter", "JUnit Jupiter"), TestFramework("spock", "Spock")),
    buildTypes = listOf(
        BuildType(
            id = "java-application",
            componentType = "application",
            language = "java",
            dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
            modularization = Choice(listOf("single-project", "with-library-projects"), "single-project"),
            testFrameworks = listOf(
                TestFrameworkChoice("single-project", listOf("junit-jupiter", "spock"), "junit-jupiter"),
                TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter"),
            ),
            supportsPackage = true,
            supportsJavaVersion = true,
            supportsProjectName = true,
            furtherReading = "https://docs.gradle.org/9.8.2/samples/sample_building_java_applications.html",
        ),
        BuildType(
            id = "basic",
            componentType = "basic",
            language = null,
            dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
            modularization = Choice(listOf("single-project"), "single-project"),
            testFrameworks = listOf(TestFrameworkChoice("single-project", emptyList(), null)),
            supportsPackage = false,
            supportsJavaVersion = false,
            supportsProjectName = true,
            furtherReading = null,
        ),
    ),
    javaVersion = JavaVersionParameter(minimum = 7, default = 21, maximum = 31),
    packageName = TextParameter(pattern = "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$", default = "org.example"),
    projectName = TextParameter(pattern = null, default = null),
)
