package gradlenew.domain.generation

import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.FileSet
import gradlenew.substitution.Substitution

fun interface TemplateSource {
    fun template(version: String, key: String): FileSet?
}

sealed interface GenerationResult {
    data class Generated(val files: FileSet, val filename: String, val immutable: Boolean) : GenerationResult
    data class Rejected(val message: String, val parameter: String? = null) : GenerationResult
}

class BuildGeneration(
    private val offered: OfferedVersions,
    private val models: (String) -> InitModel,
    private val indexes: (String) -> TemplateIndex = { TemplateIndex.NONE },
    private val templates: TemplateSource,
) {
    fun generate(parameters: List<Pair<String, String>>): GenerationResult {
        val repeated = parameters.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        if (repeated.isNotEmpty()) return GenerationResult.Rejected("Option '${repeated.first()}' is given more than once.", repeated.first())
        return generate(parameters.toMap())
    }

    fun generate(options: Map<String, String>): GenerationResult =
        when (val resolution = RequestValidation.resolve(options, offered, indexes, models)) {
            is Resolution.Rejected -> GenerationResult.Rejected(resolution.message, resolution.parameter)
            is Resolution.Accepted -> generate(resolution.request)
        }

    private fun generate(request: ResolvedRequest): GenerationResult.Generated {
        val template = checkNotNull(templates.template(request.version, request.spec.key)) {
            "No template '${request.spec.key}' was published for Gradle ${request.version}"
        }
        return GenerationResult.Generated(
            files = Substitution.apply(template, request.inputs).under(request.archiveRoot),
            filename = request.filename,
            immutable = !request.fromAlias,
        )
    }
}
