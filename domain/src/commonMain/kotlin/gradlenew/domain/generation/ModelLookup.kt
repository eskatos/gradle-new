package gradlenew.domain.generation

import gradlenew.domain.model.OfferedVersions

sealed interface ModelLookup {
    data class Found(val version: String, val immutable: Boolean) : ModelLookup
    data class Rejected(val message: String, val parameter: String? = null) : ModelLookup

    companion object {
        fun resolve(parameters: List<Pair<String, String>>, offered: OfferedVersions): ModelLookup {
            val repeated = parameters.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
            if (repeated.isNotEmpty()) return Rejected("Option '${repeated.first()}' is given more than once.", repeated.first())
            return RequestValidation.resolveModelVersion(parameters.toMap(), offered)
        }
    }
}
