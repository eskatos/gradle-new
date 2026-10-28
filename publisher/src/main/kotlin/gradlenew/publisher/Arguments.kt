package gradlenew.publisher

import java.io.File

data class Arguments(
    val stable: String,
    val rc: String?,
    val builderSources: File,
    val versionCatalog: File,
    val output: File,
    val workDir: File,
) {
    companion object {
        fun parse(args: List<String>): Arguments {
            val options = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val name = args[i]
                require(name.startsWith("--") && i + 1 < args.size) { "Expected --option value pairs, got '$name'" }
                options[name.removePrefix("--")] = args[i + 1]
                i += 2
            }
            fun required(name: String) = requireNotNull(options[name]) { "Missing --$name" }
            return Arguments(
                stable = required("stable"),
                rc = options["rc"]?.takeIf { it.isNotBlank() },
                builderSources = File(required("builder-dir")),
                versionCatalog = File(required("catalog")),
                output = File(required("output")),
                workDir = File(required("work-dir")),
            )
        }
    }
}
