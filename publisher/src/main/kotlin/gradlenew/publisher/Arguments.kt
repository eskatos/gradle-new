package gradlenew.publisher

import java.io.File

data class Arguments(
    val stable: String,
    val rc: String?,
    val builderSources: File,
    val versionCatalog: File,
    val output: File,
    val templatesOutput: File,
    val reportOutput: File,
    val workDir: File,
    val templateBudgetBytes: Long,
    val gradleArguments: List<String>,
) {
    companion object {
        private const val MEGABYTE = 1024L * 1024
        private const val DEFAULT_BUDGET_MEGABYTES = 64L
        private val repeatable = setOf("gradle-argument")

        fun parse(args: List<String>): Arguments {
            val options = mutableMapOf<String, String>()
            val gradleArguments = mutableListOf<String>()
            var i = 0
            while (i < args.size) {
                val name = args[i]
                require(name.startsWith("--") && i + 1 < args.size) { "Expected --option value pairs, got '$name'" }
                val option = name.removePrefix("--")
                if (option in repeatable) {
                    gradleArguments += args[i + 1]
                } else {
                    require(options.put(option, args[i + 1]) == null) { "Option --$option is given more than once" }
                }
                i += 2
            }
            fun required(name: String) = requireNotNull(options[name]) { "Missing --$name" }
            val budget = options["template-budget-mb"]?.let { requireNotNull(it.toLongOrNull()) { "--template-budget-mb must be a number of megabytes, got '$it'" } }
            return Arguments(
                stable = required("stable"),
                rc = options["rc"]?.takeIf { it.isNotBlank() },
                builderSources = File(required("builder-dir")),
                versionCatalog = File(required("catalog")),
                output = File(required("output")),
                templatesOutput = File(required("templates-output")),
                reportOutput = File(required("report-output")),
                workDir = File(required("work-dir")),
                templateBudgetBytes = (budget ?: DEFAULT_BUDGET_MEGABYTES) * MEGABYTE,
                gradleArguments = gradleArguments,
            )
        }
    }
}
