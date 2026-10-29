package gradlenew.domain.generation

import gradlenew.domain.model.BuildType
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.ProjectInputs

data class ResolvedRequest(val version: String, val fromAlias: Boolean, val spec: TemplateSpec, val inputs: ProjectInputs) {
    val filename: String get() = "${inputs.projectName}.zip"
    val archiveRoot: String get() = inputs.projectName
    val cacheKey: String
        get() = listOf(version, spec.key, inputs.projectName, inputs.packageName.orEmpty(), inputs.javaVersion?.toString().orEmpty()).joinToString("\n")
}

sealed interface Resolution {
    data class Accepted(val request: ResolvedRequest) : Resolution
    data class Rejected(val message: String) : Resolution
}

object RequestValidation {

    const val MAX_VALUE_LENGTH = 256

    val options = listOf("version", "type", "dsl", "test-framework", "split-project", "incubating", "comments", "project-name", "package", "java-version")

    private val projectNamePattern = Regex("^[A-Za-z]([A-Za-z0-9._-]*[A-Za-z0-9_-])?$")

    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "_", "true", "false", "null",
    )

    private class Rejection(message: String) : Exception(message)

    fun resolve(options: Map<String, String>, offered: OfferedVersions, modelFor: (String) -> InitModel): Resolution =
        try {
            Resolution.Accepted(Resolver(options).resolve(offered, modelFor))
        } catch (rejection: Rejection) {
            Resolution.Rejected(rejection.message!!)
        }

    private class Resolver(private val options: Map<String, String>) {

        fun resolve(offered: OfferedVersions, modelFor: (String) -> InitModel): ResolvedRequest {
            checkOptions()
            val (version, fromAlias) = version(offered)
            val model = modelFor(version)
            val type = type(model)
            val javaVersion = javaVersion(model, type)
            val projectName = projectName(type)
            val modularization = modularization(type)
            val dsl = dsl(model, type)
            val testFramework = testFramework(type, modularization)
            val packageName = packageName(model, type)
            val incubating = boolean("incubating", default = false)
            val comments = boolean("comments", default = true)
            return ResolvedRequest(
                version = version,
                fromAlias = fromAlias,
                spec = TemplateSpec(type.id, dsl, testFramework, modularization, incubating, comments),
                inputs = ProjectInputs(projectName, packageName, javaVersion),
            )
        }

        private fun value(name: String): String? = options[name]?.takeIf { it.isNotEmpty() }

        private fun checkOptions() {
            for ((name, value) in options) {
                if (name !in RequestValidation.options) reject("Unknown option '$name'. Supported options:" + list(RequestValidation.options))
                if (value.length > MAX_VALUE_LENGTH) reject("Value of option '$name' is too long (at most $MAX_VALUE_LENGTH characters).")
            }
        }

        private fun version(offered: OfferedVersions): Pair<String, Boolean> {
            val offeredVersions = listOfNotNull(offered.stable, offered.rc)
            return when (val requested = value("version")) {
                null, "stable" -> offered.stable to true
                "rc" -> (offered.rc ?: reject("No release candidate is currently offered.")) to true
                in offeredVersions -> requested to false
                else -> reject("Gradle version '$requested' is not offered. Offered versions:" + list(offeredVersions))
            }
        }

        private fun type(model: InitModel): BuildType {
            val requested = value("type") ?: reject("Missing required option 'type'.")
            return model.buildTypes.firstOrNull { it.id == requested }
                ?: reject("The requested build type '$requested' is not supported. Supported types:" + list(model.buildTypes.map { it.id }.sorted()))
        }

        private fun javaVersion(model: InitModel, type: BuildType): Int? {
            if (!type.supportsJavaVersion) return null
            val requested = value("java-version") ?: return model.javaVersion.default
            val parsed = requested.toIntOrNull() ?: reject("Invalid target Java version '$requested'. The version must be an integer.")
            if (parsed < model.javaVersion.minimum) {
                reject("Target Java version: '$requested' is not a supported target version. It must be equal to or greater than ${model.javaVersion.minimum}")
            }
            return parsed
        }

        private fun projectName(type: BuildType): String {
            val requested = value("project-name")
            if (!type.supportsProjectName) {
                if (requested != null) reject("Project name is not supported for '${type.id}' build type.")
                reject("Build type '${type.id}' does not take a project name, which this service requires.")
            }
            requested ?: reject("Missing required option 'project-name'.")
            if (!projectNamePattern.matches(requested)) {
                reject("Project name: '$requested' is not valid - it must start with a letter, contain only letters, digits, '.', '_' and '-', and not end with '.'.")
            }
            return requested
        }

        private fun modularization(type: BuildType): String {
            val split = boolean("split-project", default = type.modularization.default == TemplateSpec.WITH_LIBRARY_PROJECTS)
            val modularization = if (split) TemplateSpec.WITH_LIBRARY_PROJECTS else type.modularization.values.first { it != TemplateSpec.WITH_LIBRARY_PROJECTS }
            if (modularization !in type.modularization.values) reject("Split project is not supported for '${type.id}' build type.")
            return modularization
        }

        private fun dsl(model: InitModel, type: BuildType): String {
            val requested = value("dsl") ?: return type.dsls.default
            if (model.dsls.none { it.id == requested }) {
                reject("The requested build script DSL '$requested' is not supported. Supported DSLs:" + list(model.dsls.map { it.id }))
            }
            if (requested !in type.dsls.values) reject("The requested DSL '$requested' is not supported for '${type.id}' build type")
            return requested
        }

        private fun testFramework(type: BuildType, modularization: String): String? {
            val choice = type.testFrameworksFor(modularization)
            val requested = value("test-framework") ?: return choice.default
            val supported = choice.values.ifEmpty { listOf(TemplateSpec.NO_TEST_FRAMEWORK) }
            if (requested !in supported) {
                reject("The requested test framework '$requested' is not supported for '${type.id}' build type. Supported frameworks:" + list(supported))
            }
            return requested.takeIf { choice.values.isNotEmpty() }
        }

        private fun packageName(model: InitModel, type: BuildType): String? {
            val requested = value("package")
            if (!type.supportsPackage) {
                if (requested != null) reject("Package name is not supported for '${type.id}' build type.")
                return null
            }
            val packageName = requested ?: model.packageName.default ?: reject("Missing required option 'package'.")
            if (!isValidPackage(model, packageName)) {
                reject("Package name: '$packageName' is not valid - it may contain invalid characters or reserved words.")
            }
            if ('$' in packageName) reject("Package name: '$packageName' is not supported by this service - it must not contain '\$'.")
            return packageName
        }

        private fun isValidPackage(model: InitModel, packageName: String): Boolean {
            val pattern = model.packageName.pattern
            if (pattern != null && !Regex(pattern).matches(packageName)) return false
            return packageName.split('.').none { it.isEmpty() || it in javaKeywords }
        }

        private fun boolean(name: String, default: Boolean): Boolean = when (val requested = value(name)) {
            null -> default
            "true" -> true
            "false" -> false
            else -> reject("Invalid value '$requested' for option '$name'. Expected 'true' or 'false'.")
        }

        private fun list(values: List<String>) = values.joinToString("") { "\n    - '$it'" }

        private fun reject(message: String): Nothing = throw Rejection(message)
    }
}
