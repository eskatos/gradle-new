package gradlenew.adaptertoolingapi

import gradlenew.domain.model.InitModel
import java.io.File

object InitModelRequest {

    fun request(gradle: GradleDistribution, jars: BuilderJars, workDir: File): InitModel {
        val projectDir = workDir.resolve("model-request").apply {
            mkdirs()
            resolve("settings.gradle.kts").writeText("")
            resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx256m\norg.gradle.daemon.idletimeout=120000\n")
        }
        val initScript = workDir.resolve("inject-builder.gradle").apply { writeText(initScript(jars)) }
        val model = gradle.connect(projectDir) { connection ->
            connection.model(gradlenew.builder.api.InitModel::class.java)
                .withArguments("--init-script", initScript.absolutePath, "--quiet")
                .get()
        }
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
