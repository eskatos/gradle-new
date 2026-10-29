package gradlenew.adaptertoolingapi

import gradlenew.domain.model.InitModel
import java.nio.file.Files

object InitHelp {

    fun optionValues(gradle: GradleDistribution): Map<String, Set<String>> {
        val values = mutableMapOf<String, MutableSet<String>>()
        var current: MutableSet<String>? = null
        for (line in gradle.runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline")).lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("--") -> current = values.getOrPut(trimmed.substring(2).substringBefore(' ')) { linkedSetOf() }
                trimmed == "Available values are:" -> Unit
                trimmed.isEmpty() -> current = null
                current != null && !trimmed.contains(' ') -> current.add(trimmed)
            }
        }
        return values.filterValues { it.isNotEmpty() }
    }

    fun optionDescriptions(gradle: GradleDistribution): Map<String, String> =
        gradle.runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline")).lines()
            .map { it.trim() }
            .filter { it.startsWith("--") }
            .associate { it.substring(2).substringBefore(' ') to it.substringAfter(' ').trim() }

    fun model(gradle: GradleDistribution): InitModel {
        val workDir = Files.createTempDirectory("model-request").toFile()
        return InitModelRequest.request(gradle, BuilderCompiler.compile(gradle, Pinned.builderSources, Pinned.versionCatalog, workDir), workDir)
    }
}
