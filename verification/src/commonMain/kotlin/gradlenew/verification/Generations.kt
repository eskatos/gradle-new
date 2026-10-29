package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateSpec
import gradlenew.substitution.FileSet
import gradlenew.substitution.ProjectInputs

sealed interface InitOutcome {
    data class Generated(val files: FileSet) : InitOutcome
    data class Rejected(val reason: String) : InitOutcome
}

fun interface InitGenerator {
    fun generate(spec: TemplateSpec, inputs: ProjectInputs): InitOutcome
}

class Generations(private val init: InitGenerator) {

    private val templates = mutableMapOf<CatalogEntry, FileSet?>()
    private val recordedRejections = linkedSetOf<Failure>()

    val rejections: List<Failure> get() = recordedRejections.toList()

    fun template(entry: CatalogEntry): FileSet? = templates.getOrPut(entry) { fresh(entry.spec, entry.placeholders) }

    fun cachedOrFresh(entry: CatalogEntry): FileSet? =
        if (entry in templates) templates[entry] else fresh(entry.spec, entry.placeholders)

    fun fresh(spec: TemplateSpec, inputs: ProjectInputs, subject: String = spec.key): FileSet? =
        when (val outcome = init.generate(spec, inputs)) {
            is InitOutcome.Generated -> outcome.files
            is InitOutcome.Rejected -> {
                recordedRejections += Failure(subject, "gradle init rejected the entry: ${outcome.reason}")
                null
            }
        }
}

internal fun describe(differences: List<String>): String = differences.joinToString("; ")
