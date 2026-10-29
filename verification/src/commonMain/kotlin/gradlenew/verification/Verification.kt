package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.substitution.FileSet
import gradlenew.substitution.Placeholders

fun interface TemplateSink {
    fun write(entry: CatalogEntry, files: FileSet): Long
}

class Verification(private val init: InitGenerator, private val sink: TemplateSink, private val budgetBytes: Long) {

    fun verify(model: InitModel): VersionReport {
        val generations = Generations(init)
        val sample = Sampling.entries(model)

        val determinism = DeterminismCheck.run(sample, generations)
        val java = JavaVersionCheck.run(model, sample, generations)
        val differential = DifferentialCheck.run(model, sample, java.enumeratedTypes, generations)
        val sampledHygiene = hygiene(model, sample.mapNotNull { entry -> generations.template(entry)?.let { entry to it } })
        val sampled = listOf(ModelCheck.run(model, generations.rejections), determinism, java.result, differential, sampledHygiene)
        if (sampled.any { it.outcome != Outcome.PASSED }) {
            return VersionReport(model.gradleVersion, sampled + CheckResult.skipped(Check.BUDGET), java.enumeratedTypes)
        }

        val inspected = mutableListOf<Pair<CatalogEntry, FileSet>>()
        var totalBytes = 0L
        val hygieneFailures = mutableListOf<Failure>()
        for (entry in TemplateCatalog.of(model)) {
            val files = generations.cachedOrFresh(entry) ?: continue
            hygieneFailures += hygieneOf(model, entry, files)
            inspected += entry to files
            totalBytes += sink.write(entry, files)
        }
        val hygiene = CheckResult.of(Check.HYGIENE, inspected.size, hygieneFailures)
        val budget = CheckResult.of(
            Check.BUDGET,
            inspected.size,
            if (totalBytes > budgetBytes) listOf(Failure(model.gradleVersion, "templates take $totalBytes bytes, over the budget of $budgetBytes bytes")) else emptyList(),
        )
        return VersionReport(
            model.gradleVersion,
            listOf(ModelCheck.run(model, generations.rejections), determinism, java.result, differential, hygiene, budget),
            java.enumeratedTypes,
        )
    }

    private fun hygiene(model: InitModel, templates: List<Pair<CatalogEntry, FileSet>>): CheckResult =
        CheckResult.of(Check.HYGIENE, templates.size, templates.flatMap { (entry, files) -> hygieneOf(model, entry, files) })

    private fun hygieneOf(model: InitModel, entry: CatalogEntry, files: FileSet): List<Failure> {
        val inputs = Sampling.inputsFor(model.buildType(entry.spec.type), Sampling.realInputs.first())
        val substitutesJava = entry.placeholders.javaVersion == Placeholders.JAVA_VERSION
        return HygieneCheck.inspect(entry.spec.key, files, if (substitutesJava) inputs else inputs.copy(javaVersion = null))
    }
}
