package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.FileSet
import java.io.File

class SitePublisher(
    private val modelFor: (version: String) -> InitModel,
    private val templateFor: (version: String, entry: CatalogEntry) -> FileSet,
) {

    fun publish(versions: OfferedVersions, output: File) {
        output.deleteRecursively()
        output.mkdirs()
        output.resolve("versions.json").writeText(VersionsJson.encode(versions) + "\n")
        for (version in listOfNotNull(versions.stable, versions.rc)) {
            val model = modelFor(version)
            require(model.gradleVersion == version) { "Model reports Gradle ${model.gradleVersion}, expected $version" }
            val versionDir = output.resolve("v/$version").apply { mkdirs() }
            versionDir.resolve("model.json").writeText(InitModelJson.encode(model) + "\n")
            val templates = versionDir.resolve("templates").apply { mkdirs() }
            for (entry in TemplateCatalog.of(model)) {
                templates.resolve("${entry.spec.key}.zip").writeBytes(Zip.write(templateFor(version, entry)))
            }
        }
    }
}
