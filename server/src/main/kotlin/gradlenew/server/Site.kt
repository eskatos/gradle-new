package gradlenew.server

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.BuildGeneration
import gradlenew.domain.generation.TemplateIndex
import gradlenew.domain.model.InitModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class Site(private val root: File, private val templatesRoot: File) {

    private val models = ConcurrentHashMap<String, InitModel>()
    private val indexes = ConcurrentHashMap<String, TemplateIndex>()

    val offered by lazy { VersionsJson.decode(root.resolve("versions.json").readText()) }

    fun modelFile(version: String): File = root.resolve("v/$version/model.json")

    val generation: BuildGeneration by lazy {
        BuildGeneration(
            offered = offered,
            models = { version -> models.computeIfAbsent(version) { InitModelJson.decode(root.resolve("v/$it/model.json").readText()) } },
            indexes = { version -> indexes.computeIfAbsent(version) { index(templatesRoot.resolve("$it/index.json")) } },
            templates = { version, key -> templatesRoot.resolve("$version/$key.zip").takeIf { it.isFile }?.let { Zip.read(it.readBytes()) } },
        )
    }

    private fun index(file: File): TemplateIndex = if (file.isFile) TemplateIndexJson.decode(file.readText()) else TemplateIndex.NONE
}
