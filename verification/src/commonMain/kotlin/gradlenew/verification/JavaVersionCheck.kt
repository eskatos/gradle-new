package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.model.InitModel
import gradlenew.substitution.Substitution

data class JavaVersionDetection(val result: CheckResult, val enumeratedTypes: List<String>)

object JavaVersionCheck {

    const val PROBE_VERSION = 98

    fun run(model: InitModel, sample: List<CatalogEntry>, generations: Generations): JavaVersionDetection {
        var checked = 0
        val enumerated = mutableListOf<String>()
        for (entry in sample) {
            if (!model.buildType(entry.spec.type).supportsJavaVersion) continue
            val template = generations.template(entry) ?: continue
            val probeInputs = entry.placeholders.copy(javaVersion = PROBE_VERSION)
            val probe = generations.fresh(entry.spec, probeInputs, "${entry.spec.key} with Java $PROBE_VERSION") ?: continue
            checked++
            if (Substitution.apply(template, probeInputs) != probe) enumerated += entry.spec.type
        }
        return JavaVersionDetection(CheckResult.of(Check.JAVA_VERSION, checked, emptyList()), enumerated)
    }
}
