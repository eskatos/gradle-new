package gradlenew.verification

import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.generation.TemplateSpec
import gradlenew.substitution.Derivations
import gradlenew.substitution.ProjectInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplingTest {

    private val model = model()

    @Test
    fun samplesTheDefaultCombinationOfEveryBuildTypeOnce() {
        val sample = Sampling.entries(model)

        assertEquals(model.buildTypes.map { it.id }, sample.map { it.spec.type })
        assertTrue(sample.all { it in TemplateCatalog.of(model) })
        assertEquals(
            TemplateSpec("java-application", "kotlin", "junit-jupiter", "single-project", incubating = false, comments = true),
            sample.first { it.spec.type == "java-application" }.spec,
        )
        assertEquals(TemplateSpec("basic", "kotlin", null, "single-project", incubating = false, comments = true), sample.last().spec)
    }

    @Test
    fun isDeterministic() {
        assertEquals(Sampling.entries(model), Sampling.entries(model))
    }

    @Test
    fun realInputsExerciseEveryDerivation() {
        val names = Sampling.realInputs.map { it.projectName }
        assertTrue(names.any { '-' in it } && names.any { '_' in it } && names.any { '.' in it } && names.any { it.any(Char::isDigit) })
        assertTrue(names.any { name -> name.all { it.isLetter() } }, names.toString())
        assertTrue(names.all { Derivations.toNamespace(it).isNotEmpty() })
        assertTrue(names.any { Derivations.capitalize(Derivations.toCamelCase(it)) != Derivations.capitalize(it) })

        val packages = Sampling.realInputs.mapNotNull { it.packageName }
        assertTrue(packages.any { '.' !in it } && packages.any { it.count { c -> c == '.' } >= 3 }, packages.toString())
        assertEquals(setOf(8, 17, 25), Sampling.realInputs.mapNotNull { it.javaVersion }.toSet())
    }

    @Test
    fun adaptsRealInputsToWhatATypeTakes() {
        val input = ProjectInputs("demo-app", "com.acme.demo", 17)

        assertEquals(input, Sampling.inputsFor(javaApplication, input))
        assertEquals(ProjectInputs("demo-app", "com.acme.demo", null), Sampling.inputsFor(kotlinPlugin, input))
        val cpp = Sampling.inputsFor(cppApplication, input)
        assertNull(cpp.packageName)
        assertNull(cpp.javaVersion)
    }
}
