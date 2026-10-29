package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.BuildType
import gradlenew.domain.model.InitModel
import gradlenew.substitution.ProjectInputs

object Sampling {

    val realInputs: List<ProjectInputs> = listOf(
        ProjectInputs("demo-app", "com.acme.demo", 17),
        ProjectInputs("my_cool.thing2", "org.example.deep.nested.pkg", 8),
        ProjectInputs("app", "acme", 25),
    )

    fun entries(model: InitModel): List<CatalogEntry> {
        val catalog = TemplateCatalog.of(model)
        return model.buildTypes.map { type ->
            val modularization = type.modularization.default
            catalog.first {
                it.spec.type == type.id &&
                    it.spec.dsl == type.dsls.default &&
                    it.spec.modularization == modularization &&
                    it.spec.testFramework == type.testFrameworksFor(modularization).default &&
                    !it.spec.incubating &&
                    it.spec.comments
            }
        }
    }

    fun inputsFor(type: BuildType, inputs: ProjectInputs) = ProjectInputs(
        projectName = inputs.projectName,
        packageName = inputs.packageName.takeIf { type.supportsPackage },
        javaVersion = inputs.javaVersion.takeIf { type.supportsJavaVersion },
    )
}
