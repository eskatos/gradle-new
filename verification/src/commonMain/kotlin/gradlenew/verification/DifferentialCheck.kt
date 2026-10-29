package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.model.InitModel
import gradlenew.substitution.FileSet
import gradlenew.substitution.Substitution

object DifferentialCheck {

    fun run(model: InitModel, sample: List<CatalogEntry>, enumeratedTypes: List<String>, generations: Generations): CheckResult {
        var checked = 0
        val failures = mutableListOf<Failure>()
        for (entry in sample) {
            val type = model.buildType(entry.spec.type)
            for (real in Sampling.realInputs) {
                val inputs = Sampling.inputsFor(type, real)
                val subject = "${entry.spec.key} with ${inputs.projectName}"
                val enumerated = type.id in enumeratedTypes
                val template = if (enumerated) {
                    generations.fresh(entry.spec, entry.placeholders.copy(javaVersion = inputs.javaVersion), "${entry.spec.key} with Java ${inputs.javaVersion}")
                } else {
                    generations.template(entry)
                } ?: continue
                val expected = generations.fresh(entry.spec, inputs, subject) ?: continue
                checked++
                val actual = substitute(template, if (enumerated) inputs.copy(javaVersion = null) else inputs)
                    .getOrElse { failures += Failure(subject, it.message ?: "substitution failed"); continue }
                val differences = expected.differencesFrom(actual)
                if (differences.isNotEmpty()) failures += Failure(subject, describe(differences))
            }
        }
        return CheckResult.of(Check.DIFFERENTIAL, checked, failures)
    }

    private fun substitute(template: FileSet, inputs: gradlenew.substitution.ProjectInputs): Result<FileSet> =
        try {
            Result.success(Substitution.apply(template, inputs))
        } catch (failure: IllegalArgumentException) {
            Result.failure(failure)
        }
}
