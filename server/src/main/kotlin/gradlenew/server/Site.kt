package gradlenew.server

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.BuildGeneration
import gradlenew.domain.model.InitModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class Site(private val root: File, private val templatesRoot: File) {

    private val models = ConcurrentHashMap<String, InitModel>()

    val generation: BuildGeneration by lazy {
        BuildGeneration(
            offered = VersionsJson.decode(root.resolve("versions.json").readText()),
            models = { version -> models.computeIfAbsent(version) { InitModelJson.decode(root.resolve("v/$it/model.json").readText()) } },
            templates = { version, key -> templatesRoot.resolve("$version/$key.zip").takeIf { it.isFile }?.let { Zip.read(it.readBytes()) } },
        )
    }
}
