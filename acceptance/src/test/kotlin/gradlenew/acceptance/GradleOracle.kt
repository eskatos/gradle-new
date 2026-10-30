package gradlenew.acceptance

import gradlenew.adaptertoolingapi.GradleDistribution
import gradlenew.adaptertoolingapi.InitRejected
import gradlenew.adaptertoolingapi.InitRunner
import java.io.File
import java.nio.file.Files

class GradleOracle(version: String, javaHome: File? = null) {

    private val gradle = GradleDistribution(version, listOfNotNull(javaHome?.let { "-Dorg.gradle.java.home=${it.absolutePath}" }))

    fun initOptionValues(): Map<String, Set<String>> {
        val help = gradle.runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline"))
        return parseOptionValues(help)
    }

    fun initOptionDescriptions(): Map<String, String> =
        parseOptionDescriptions(gradle.runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline")))

    fun initWithArguments(arguments: List<String>): Map<String, GeneratedFile> {
        val dir = Files.createTempDirectory("gradle-oracle-init").toFile()
        try {
            gradle.runTasks(dir, listOf("init", "--quiet", "--offline") + arguments)
            return GeneratedBuild.fromDirectory(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    fun initRejection(arguments: List<String>): String? =
        try {
            InitRunner(gradle).run(listOf("--project-name", "probe") + arguments)
            null
        } catch (rejected: InitRejected) {
            rejected.reason
        }

    fun init(options: List<Pair<String, String>>): Map<String, GeneratedFile> {
        val dir = Files.createTempDirectory("gradle-oracle-init").toFile()
        try {
            gradle.runTasks(dir, listOf("init", "--quiet", "--offline", "--use-defaults") + initArguments(options))
            return GeneratedBuild.fromDirectory(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun initArguments(options: List<Pair<String, String>>): List<String> =
        options.flatMap { (name, value) ->
            when (value) {
                "true" -> listOf("--$name")
                "false" -> listOf("--no-$name")
                else -> listOf("--$name", value)
            }
        }

    private fun emptyBuild(): File =
        Files.createTempDirectory("gradle-oracle").toFile().apply {
            resolve("settings.gradle.kts").writeText("")
            resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx256m\n")
        }

    companion object {
        fun parseOptionDescriptions(help: String): Map<String, String> =
            help.lines().map { it.trim() }.filter { it.startsWith("--") }.associate { line ->
                line.substring(2).substringBefore(' ') to line.substringAfter(' ').trim()
            }

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
