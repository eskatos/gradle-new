package gradlenew.verification

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
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import gradlenew.substitution.ProjectInputs

private fun type(
    id: String,
    component: String,
    language: String?,
    modularization: List<String> = listOf("single-project"),
    testFrameworks: Map<String, List<String>> = modularization.associateWith { emptyList() },
    supportsPackage: Boolean,
    supportsJavaVersion: Boolean,
) = BuildType(
    id = id,
    componentType = component,
    language = language,
    dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
    modularization = Choice(modularization, "single-project"),
    testFrameworks = modularization.map { TestFrameworkChoice(it, testFrameworks.getValue(it), testFrameworks.getValue(it).lastOrNull()) },
    supportsPackage = supportsPackage,
    supportsJavaVersion = supportsJavaVersion,
    supportsProjectName = true,
    furtherReading = null,
)

val javaApplication = type(
    "java-application", "application", "java",
    modularization = listOf("single-project", "with-library-projects"),
    testFrameworks = mapOf("single-project" to listOf("junit", "junit-jupiter"), "with-library-projects" to listOf("junit-jupiter")),
    supportsPackage = true, supportsJavaVersion = true,
)
val groovyLibrary = type("groovy-library", "library", "groovy", testFrameworks = mapOf("single-project" to listOf("spock")), supportsPackage = true, supportsJavaVersion = true)
val kotlinPlugin = type("kotlin-gradle-plugin", "gradle-plugin", "kotlin", testFrameworks = mapOf("single-project" to listOf("kotlintest")), supportsPackage = true, supportsJavaVersion = false)
val cppApplication = type("cpp-application", "application", "cpp", testFrameworks = mapOf("single-project" to listOf("cpptest")), supportsPackage = false, supportsJavaVersion = false)
val basic = type("basic", "basic", null, supportsPackage = false, supportsJavaVersion = false)

fun model(vararg buildTypes: BuildType = arrayOf(javaApplication, groovyLibrary, kotlinPlugin, cppApplication, basic)) = InitModel(
    gradleVersion = "9.8.2",
    componentTypes = listOf("application", "library", "gradle-plugin", "basic").map { ComponentType(it, it, emptyList()) },
    languages = listOf("java", "groovy", "kotlin", "cpp").map { Language(it, it) },
    dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts"), Dsl("groovy", "Groovy", ".gradle")),
    modularizationOptions = listOf(ModularizationOption("single-project", "Single"), ModularizationOption("with-library-projects", "Split")),
    testFrameworks = listOf("junit", "junit-jupiter", "spock", "kotlintest", "cpptest").map { TestFramework(it, it) },
    buildTypes = buildTypes.toList(),
    javaVersion = JavaVersionParameter(minimum = 7, default = 21, maximum = 31),
    packageName = TextParameter("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$", "org.example"),
    projectName = TextParameter(null, null),
)

fun text(path: String, content: String, executable: Boolean = false) = FileEntry(path, content.encodeToByteArray(), executable)

fun fakeInit(inputs: ProjectInputs, type: String, extra: List<FileEntry> = emptyList()): FileSet {
    val packagePath = inputs.packageName?.replace('.', '/')
    return FileSet.of(
        buildList {
            add(text("settings.gradle.kts", "rootProject.name = \"${inputs.projectName}\" // $type"))
            add(FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true))
            add(FileEntry("gradle/wrapper/gradle-wrapper.jar", byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00, 0xff.toByte()), false))
            if (packagePath != null) add(text("src/main/java/$packagePath/App.java", "package ${inputs.packageName};"))
            if (inputs.javaVersion != null) add(text("build.gradle.kts", "languageVersion = JavaLanguageVersion.of(${inputs.javaVersion})"))
            addAll(extra)
        },
    )
}
