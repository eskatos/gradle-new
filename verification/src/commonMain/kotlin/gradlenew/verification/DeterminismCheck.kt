package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry

object DeterminismCheck {

    fun run(sample: List<CatalogEntry>, generations: Generations): CheckResult {
        var checked = 0
        val failures = mutableListOf<Failure>()
        for (entry in sample) {
            val first = generations.template(entry) ?: continue
            val second = generations.fresh(entry.spec, entry.placeholders) ?: continue
            checked++
            val differences = first.differencesFrom(second)
            if (differences.isNotEmpty()) failures += Failure(entry.spec.key, describe(differences))
        }
        return CheckResult.of(Check.DETERMINISM, checked, failures)
    }
}
