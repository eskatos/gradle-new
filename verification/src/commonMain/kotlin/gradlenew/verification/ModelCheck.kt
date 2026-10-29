package gradlenew.verification

import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel

object ModelCheck {

    fun run(model: InitModel, rejections: List<Failure>): CheckResult {
        val failures = mutableListOf<Failure>()
        val invariants = runCatching { model.copy() }.exceptionOrNull()
        if (invariants != null) failures += Failure(model.gradleVersion, "the model breaks an invariant: ${invariants.message}")
        if (model.buildTypes.isEmpty()) failures += Failure(model.gradleVersion, "the model offers no build types")
        val catalogTypes = TemplateCatalog.of(model).map { it.spec.type }.toSet()
        for (type in model.buildTypes.filterNot { it.id in catalogTypes }) {
            failures += Failure(type.id, "the build type has no catalog entries")
        }
        return CheckResult.of(Check.MODEL, model.buildTypes.size, failures + rejections)
    }

    fun unavailable(version: String, reason: String): CheckResult =
        CheckResult.of(Check.MODEL, 0, listOf(Failure(version, "the model could not be obtained: $reason")))
}
