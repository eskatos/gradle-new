package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import java.io.File

class SitePublisher(private val modelFor: (version: String) -> InitModel) {

    fun publish(versions: OfferedVersions, output: File) {
        output.deleteRecursively()
        output.mkdirs()
        output.resolve("versions.json").writeText(VersionsJson.encode(versions) + "\n")
        for (version in listOfNotNull(versions.stable, versions.rc)) {
            val model = modelFor(version)
            require(model.gradleVersion == version) { "Model reports Gradle ${model.gradleVersion}, expected $version" }
            output.resolve("v/$version").apply { mkdirs() }.resolve("model.json").writeText(InitModelJson.encode(model) + "\n")
        }
    }
}
