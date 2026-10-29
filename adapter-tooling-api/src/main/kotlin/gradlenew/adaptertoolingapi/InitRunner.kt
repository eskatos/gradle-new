package gradlenew.adaptertoolingapi

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import java.io.File
import java.nio.file.Files

class InitRejected(val reason: String, output: String) : RuntimeException("gradle init rejected the request: $reason\n$output")

class InitRunner(private val gradle: GradleDistribution) {

    fun run(arguments: List<String>): FileSet {
        val dir = Files.createTempDirectory("gradle-init").toFile()
        try {
            try {
                gradle.runTasks(dir, listOf("init", "--use-defaults", "--offline", "--quiet") + arguments, searchUpwards = false)
            } catch (failure: GradleBuildFailed) {
                throw InitRejected(reasonFrom(failure.output), failure.output)
            }
            return read(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun read(dir: File): FileSet = FileSet.of(
        dir.walkTopDown()
            .onEnter { it == dir || it.name != ".gradle" }
            .filter { it.isFile }
            .map { FileEntry(it.relativeTo(dir).invariantSeparatorsPath, it.readBytes(), it.canExecute()) }
            .toList(),
    )

    private fun reasonFrom(output: String): String {
        val lines = output.lines()
        val start = lines.indexOfFirst { it.startsWith("> ") }
        if (start < 0) return output.trim()
        val reason = mutableListOf(lines[start].removePrefix("> "))
        for (line in lines.drop(start + 1)) {
            if (line.isBlank() || line.startsWith("* ") || line.startsWith("> ")) break
            reason += line
        }
        return reason.joinToString("\n")
    }
}
