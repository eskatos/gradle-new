package gradlenew.publisher

import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.OfferedVersions
import java.io.File

object IndexCommand {

    fun run(arguments: List<String>) {
        require(arguments.size % 2 == 0 && arguments.chunked(2).all { it[0].startsWith("--") }) { "Expected --option value pairs, got $arguments" }
        val options = arguments.chunked(2).associate { (name, value) -> name.removePrefix("--") to value }
        val stable = requireNotNull(options["stable"]?.takeIf { it.isNotBlank() }) { "Missing --stable" }
        val previous = requireNotNull(options["previous"]?.takeIf { it.isNotBlank() }) { "Missing --previous" }
        val output = File(requireNotNull(options["output"]) { "Missing --output" })
        output.deleteRecursively()
        output.mkdirs()
        output.resolve("versions.json").writeText(VersionsJson.encode(OfferedVersions(stable, options["rc"]?.takeIf { it.isNotBlank() }, previous)) + "\n")
    }
}
