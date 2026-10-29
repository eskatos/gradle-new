package gradlenew.domain.generation

import gradlenew.domain.model.BuildType
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.ProjectInputs

data class ResolvedRequest(val version: String, val fromAlias: Boolean, val spec: TemplateSpec, val inputs: ProjectInputs) {
    val filename: String get() = "${inputs.projectName}.zip"
    val archiveRoot: String get() = inputs.projectName
}

sealed interface Resolution {
    data class Accepted(val request: ResolvedRequest) : Resolution
    data class Rejected(val message: String, val parameter: String? = null) : Resolution
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

    val modelOptions = listOf("version")

    private class Rejection(message: String, val parameter: String?) : Exception(message)

    fun resolveModelVersion(options: Map<String, String>, offered: OfferedVersions): ModelLookup =
        try {
            val resolver = Resolver(options, modelOptions)
            resolver.checkOptions()
            val (version, fromAlias) = resolver.version(offered)
            ModelLookup.Found(version, immutable = !fromAlias)
        } catch (rejection: Rejection) {
            ModelLookup.Rejected(rejection.message!!, rejection.parameter)
        }

    fun resolve(
        options: Map<String, String>,
        offered: OfferedVersions,
        indexFor: (String) -> TemplateIndex = { TemplateIndex.NONE },
        modelFor: (String) -> InitModel,
    ): Resolution =
        try {
            Resolution.Accepted(Resolver(options).resolve(offered, indexFor, modelFor))
        } catch (rejection: Rejection) {
            Resolution.Rejected(rejection.message!!, rejection.parameter)
        }

    private class Resolver(private val options: Map<String, String>, private val supported: List<String> = RequestValidation.options) {

        fun resolve(offered: OfferedVersions, indexFor: (String) -> TemplateIndex, modelFor: (String) -> InitModel): ResolvedRequest {
            checkOptions()
            val (version, fromAlias) = version(offered)
            val model = modelFor(version)
            val type = type(model)
            val enumerated = type.id in indexFor(version).javaEnumeratedTypes
            val javaVersion = javaVersion(model, type, enumerated)
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
                spec = TemplateSpec(type.id, dsl, testFramework, modularization, incubating, comments, javaVersion.takeIf { enumerated }),
                inputs = ProjectInputs(projectName, packageName, javaVersion.takeUnless { enumerated }),
            )
        }

        private fun value(name: String): String? = options[name]?.takeIf { it.isNotEmpty() }

        fun checkOptions() {
            for ((name, value) in options) {
                if (name !in supported) reject("Unknown option '$name'. Supported options:" + list(supported), name)
                if (value.length > MAX_VALUE_LENGTH) reject("Value of option '$name' is too long (at most $MAX_VALUE_LENGTH characters).", name)
            }
        }

        fun version(offered: OfferedVersions): Pair<String, Boolean> {
            val offeredVersions = listOfNotNull(offered.stable, offered.rc)
            return when (val requested = value("version")) {
                null, "stable" -> offered.stable to true
                "rc" -> (offered.rc ?: reject("No release candidate is currently offered.", "version")) to true
                in offeredVersions -> requested to false
                else -> reject("Gradle version '$requested' is not offered. Offered versions:" + list(offeredVersions), "version")
            }
        }

        private fun type(model: InitModel): BuildType {
            val requested = value("type") ?: reject("Missing required option 'type'.", "type")
            return model.buildTypes.firstOrNull { it.id == requested }
                ?: reject("The requested build type '$requested' is not supported. Supported types:" + list(model.buildTypes.map { it.id }.sorted()), "type")
        }

        private fun javaVersion(model: InitModel, type: BuildType, enumerated: Boolean): Int? {
            if (!type.supportsJavaVersion) return null
            val requested = value("java-version") ?: return model.javaVersion.default
            val parsed = requested.toIntOrNull() ?: reject("Invalid target Java version '$requested'. The version must be an integer.", "java-version")
            if (parsed < model.javaVersion.minimum) {
                reject("Target Java version: '$requested' is not a supported target version. It must be equal to or greater than ${model.javaVersion.minimum}", "java-version")
            }
            if (enumerated && parsed > model.javaVersion.maximum) {
                reject("Target Java version: '$requested' is not offered for '${type.id}' build type - it must be between ${model.javaVersion.minimum} and ${model.javaVersion.maximum}.", "java-version")
            }
            return parsed
        }

        private fun projectName(type: BuildType): String {
            val requested = value("project-name")
            if (!type.supportsProjectName) {
                if (requested != null) reject("Project name is not supported for '${type.id}' build type.", "project-name")
                reject("Build type '${type.id}' does not take a project name, which this service requires.", "project-name")
            }
            requested ?: reject("Missing required option 'project-name'.", "project-name")
            if (!projectNamePattern.matches(requested)) {
                reject("Project name: '$requested' is not valid - it must start with a letter, contain only letters, digits, '.', '_' and '-', and not end with '.'.", "project-name")
            }
            return requested
        }

        private fun modularization(type: BuildType): String {
            val split = boolean("split-project", default = type.modularization.default == TemplateSpec.WITH_LIBRARY_PROJECTS)
            val modularization = if (split) TemplateSpec.WITH_LIBRARY_PROJECTS else type.modularization.values.first { it != TemplateSpec.WITH_LIBRARY_PROJECTS }
            if (modularization !in type.modularization.values) reject("Split project is not supported for '${type.id}' build type.", "split-project")
            return modularization
        }

        private fun dsl(model: InitModel, type: BuildType): String {
            val requested = value("dsl") ?: return type.dsls.default
            if (model.dsls.none { it.id == requested }) {
                reject("The requested build script DSL '$requested' is not supported. Supported DSLs:" + list(model.dsls.map { it.id }), "dsl")
            }
            if (requested !in type.dsls.values) reject("The requested DSL '$requested' is not supported for '${type.id}' build type", "dsl")
            return requested
        }

        private fun testFramework(type: BuildType, modularization: String): String? {
            val choice = type.testFrameworksFor(modularization)
            val requested = value("test-framework") ?: return choice.default
            val supported = choice.values.ifEmpty { listOf(TemplateSpec.NO_TEST_FRAMEWORK) }
            if (requested !in supported) {
                reject("The requested test framework '$requested' is not supported for '${type.id}' build type. Supported frameworks:" + list(supported), "test-framework")
            }
            return requested.takeIf { choice.values.isNotEmpty() }
        }

        private fun packageName(model: InitModel, type: BuildType): String? {
            val requested = value("package")
            if (!type.supportsPackage) {
                if (requested != null) reject("Package name is not supported for '${type.id}' build type.", "package")
                return null
            }
            val packageName = requested ?: model.packageName.default ?: reject("Missing required option 'package'.", "package")
            if (!isValidPackage(model, packageName)) {
                reject("Package name: '$packageName' is not valid - it may contain invalid characters or reserved words.", "package")
            }
            if ('$' in packageName) reject("Package name: '$packageName' is not supported by this service - it must not contain '\$'.", "package")
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
            else -> reject("Invalid value '$requested' for option '$name'. Expected 'true' or 'false'.", name)
        }

        private fun list(values: List<String>) = values.joinToString("") { "\n    - '$it'" }

        private fun reject(message: String, parameter: String?): Nothing = throw Rejection(message, parameter)
    }
}
