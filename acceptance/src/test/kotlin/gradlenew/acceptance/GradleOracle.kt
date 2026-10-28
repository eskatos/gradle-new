package gradlenew.acceptance

import gradlenew.adaptertoolingapi.GradleBuildFailed
import gradlenew.adaptertoolingapi.GradleDistribution
import java.io.File
import java.nio.file.Files

class GradleOracle(version: String) {

    private val gradle = GradleDistribution(version)

    fun initOptionValues(): Map<String, Set<String>> {
        val help = gradle.runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline"))
        return parseOptionValues(help)
    }

    fun initFailure(arguments: List<String>): String? =
        try {
            gradle.runTasks(emptyBuild(), listOf("init", "--quiet", "--offline", "--overwrite", "--use-defaults", "--project-name", "probe") + arguments)
            null
        } catch (failure: GradleBuildFailed) {
            failure.output
        }

    private fun emptyBuild(): File =
        Files.createTempDirectory("gradle-oracle").toFile().apply {
            resolve("settings.gradle.kts").writeText("")
            resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx256m\norg.gradle.daemon.idletimeout=120000\n")
        }

    companion object {
        fun parseOptionValues(help: String): Map<String, Set<String>> {
            val values = mutableMapOf<String, MutableSet<String>>()
            var current: MutableSet<String>? = null
            for (line in help.lines()) {
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
    }
}
