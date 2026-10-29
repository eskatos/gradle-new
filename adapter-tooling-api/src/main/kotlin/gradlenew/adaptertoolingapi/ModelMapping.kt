package gradlenew.adaptertoolingapi

import gradlenew.builder.api.BuildType
import gradlenew.builder.api.Choice
import gradlenew.builder.api.InitModel
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.Language
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter

internal object ModelMapping {

    fun toDomain(model: InitModel): gradlenew.domain.model.InitModel = gradlenew.domain.model.InitModel(
        gradleVersion = model.gradleVersion,
        componentTypes = model.componentTypes.map { ComponentType(it.id, it.displayName, it.defaultProjectNames.toList()) },
        languages = model.languages.map { Language(it.id, it.displayName) },
        dsls = model.dsls.map { Dsl(it.id, it.displayName, it.fileExtension) },
        modularizationOptions = model.modularizationOptions.map { ModularizationOption(it.id, it.displayName) },
        testFrameworks = model.testFrameworks.map { TestFramework(it.id, it.displayName) },
        buildTypes = model.buildTypes.map { toDomain(it) },
        javaVersion = JavaVersionParameter(model.javaVersion.minimum, model.javaVersion.default, model.javaVersion.maximum),
        packageName = TextParameter(model.packageName.pattern, model.packageName.default),
        projectName = TextParameter(model.projectName.pattern, model.projectName.default),
    )

    private fun toDomain(type: BuildType) = gradlenew.domain.model.BuildType(
        id = type.id,
        componentType = type.componentType,
        language = type.language,
        dsls = toDomain(type.dsls),
        modularization = toDomain(type.modularization),
        testFrameworks = type.testFrameworks.map { TestFrameworkChoice(it.modularization, it.values.toList(), it.default) },
        supportsPackage = type.isPackageSupported,
        supportsJavaVersion = type.isJavaVersionSupported,
        supportsProjectName = type.isProjectNameSupported,
        furtherReading = type.furtherReading,
    )

    private fun toDomain(choice: Choice) = gradlenew.domain.model.Choice(choice.values.toList(), choice.default)
}
