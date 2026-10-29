package gradlenew.domain.generation

import gradlenew.domain.model.BuildType
import gradlenew.domain.model.InitModel
import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs

data class TemplateSpec(
    val type: String,
    val dsl: String,
    val testFramework: String?,
    val modularization: String,
    val incubating: Boolean,
    val comments: Boolean,
    val javaVersion: Int? = null,
) {
    val key: String
        get() = listOfNotNull(
            type,
            dsl,
            testFramework ?: NO_TEST_FRAMEWORK,
            modularization,
            if (incubating) "incubating" else "no-incubating",
            if (comments) "comments" else "no-comments",
            javaVersion?.let { "java$it" },
        ).joinToString(".")

    companion object {
        const val NO_TEST_FRAMEWORK = "none"
        const val WITH_LIBRARY_PROJECTS = "with-library-projects"
    }
}

data class CatalogEntry(val spec: TemplateSpec, val placeholders: ProjectInputs) {

    fun initArguments(): List<String> = buildList {
        add("--type"); add(spec.type)
        add("--dsl"); add(spec.dsl)
        spec.testFramework?.let { add("--test-framework"); add(it) }
        add(if (spec.modularization == TemplateSpec.WITH_LIBRARY_PROJECTS) "--split-project" else "--no-split-project")
        add(if (spec.incubating) "--incubating" else "--no-incubating")
        add(if (spec.comments) "--comments" else "--no-comments")
        add("--project-name"); add(placeholders.projectName)
        placeholders.packageName?.let { add("--package"); add(it) }
        placeholders.javaVersion?.let { add("--java-version"); add(it.toString()) }
    }
}

data class TemplateIndex(val javaEnumeratedTypes: Set<String>) {
    companion object {
        val NONE = TemplateIndex(emptySet())
    }
}

object TemplateCatalog {

    fun of(model: InitModel, javaEnumeratedTypes: Set<String> = emptySet()): List<CatalogEntry> {
        for (id in javaEnumeratedTypes) {
            require(model.buildTypes.any { it.id == id && it.supportsJavaVersion }) { "Build type '$id' does not take a Java version to enumerate" }
        }
        val javaVersions = model.javaVersion.minimum..model.javaVersion.maximum
        return model.buildTypes.flatMap { type ->
            val entries = entries(type)
            if (type.id !in javaEnumeratedTypes) entries
            else entries.flatMap { entry ->
                javaVersions.map { version -> CatalogEntry(entry.spec.copy(javaVersion = version), entry.placeholders.copy(javaVersion = version)) }
            }
        }
    }

    fun placeholdersFor(type: BuildType) = ProjectInputs(
        projectName = Placeholders.PROJECT_NAME,
        packageName = Placeholders.PACKAGE.takeIf { type.supportsPackage },
        javaVersion = Placeholders.JAVA_VERSION.takeIf { type.supportsJavaVersion },
    )

    private fun entries(type: BuildType): List<CatalogEntry> {
        val placeholders = placeholdersFor(type)
        return type.dsls.values.flatMap { dsl ->
            type.modularization.values.flatMap { modularization ->
                type.testFrameworksFor(modularization).values.ifEmpty { listOf(null) }.flatMap { testFramework ->
                    listOf(false, true).flatMap { incubating ->
                        listOf(true, false).map { comments ->
                            CatalogEntry(TemplateSpec(type.id, dsl, testFramework, modularization, incubating, comments), placeholders)
                        }
                    }
                }
            }
        }
    }
}
