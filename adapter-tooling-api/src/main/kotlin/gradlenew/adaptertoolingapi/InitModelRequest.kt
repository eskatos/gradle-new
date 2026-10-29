package gradlenew.adaptertoolingapi

import gradlenew.domain.model.InitModel
import java.io.File

object InitModelRequest {

    fun request(gradle: GradleDistribution, jars: BuilderJars, workDir: File): InitModel {
        val projectDir = workDir.resolve("model-request").apply {
            mkdirs()
            resolve("settings.gradle.kts").writeText("")
        }
        val initScript = workDir.resolve("inject-builder.gradle").apply { writeText(initScript(jars)) }
        val model = gradle.requestModel(
            projectDir,
            gradlenew.builder.api.InitModel::class.java,
            listOf("--init-script", initScript.absolutePath, "--quiet"),
        )
        return ModelMapping.toDomain(model)
    }

    private fun initScript(jars: BuilderJars): String = """
        initscript {
            dependencies {
                classpath files('${jars.plugin.absolutePath}', '${jars.api.absolutePath}')
            }
        }
        rootProject {
            pluginManager.apply(gradlenew.builder.InitModelPlugin)
        }
    """.trimIndent() + "\n"
}
