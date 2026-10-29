package gradlenew.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifferentialCheckTest {

    private val model = model()
    private val sample = Sampling.entries(model)
    private val comparisons = model.buildTypes.size * Sampling.realInputs.size

    @Test
    fun passesWhenSubstitutedTemplatesEqualInitForEveryRealInput() {
        val generations = Generations { spec, inputs -> InitOutcome.Generated(fakeInit(inputs, spec.type)) }

        assertEquals(CheckResult.of(Check.DIFFERENTIAL, comparisons, emptyList()), DifferentialCheck.run(model, sample, emptyList(), generations))
    }

    @Test
    fun namesTheTemplateTheInputAndTheFileThatDiffers() {
        val generations = Generations { spec, inputs ->
            InitOutcome.Generated(fakeInit(inputs, spec.type, listOf(text("PROJECT.txt", inputs.projectName.replace('-', ' ')))))
        }

        val result = DifferentialCheck.run(model, sample, emptyList(), generations)

        assertEquals(Outcome.FAILED, result.outcome)
        assertTrue(Failure("java-application.kotlin.junit-jupiter.single-project.no-incubating.comments with demo-app", "PROJECT.txt: content differs") in result.failures, result.toString())
        assertEquals(comparisons, result.failures.size, "the spaced placeholder form never matches any real input")
    }

    @Test
    fun comparesEnumeratedTypesAgainstTemplatesGeneratedAtTheRealJavaVersion() {
        val generations = Generations { spec, inputs ->
            val comment = if (spec.type == "groovy-library") listOf(text("lib/build.gradle", "// targets Java ${inputs.javaVersion}")) else emptyList()
            InitOutcome.Generated(fakeInit(inputs, spec.type, comment))
        }

        assertEquals(Outcome.PASSED, DifferentialCheck.run(model, sample, listOf("groovy-library"), generations).outcome)
        val substituted = DifferentialCheck.run(model, sample, emptyList(), generations)
        assertEquals(Sampling.realInputs.size, substituted.failures.size, substituted.toString())
        assertTrue(substituted.failures.all { it.subject.startsWith("groovy-library.") && it.message == "lib/build.gradle: content differs" })
    }

    @Test
    fun reportsATemplateThatCannotBeSubstituted() {
        val generations = Generations { spec, inputs ->
            val files = fakeInit(inputs, spec.type, if (spec.type == "basic") listOf(text("x.txt", "zqxpkg.zqxsub")) else emptyList())
            InitOutcome.Generated(files)
        }

        val failures = DifferentialCheck.run(model, sample, emptyList(), generations).failures

        assertEquals(Sampling.realInputs.size, failures.size)
        assertTrue(failures.all { it.subject.startsWith("basic.") && it.message.contains("package placeholder") }, failures.toString())
    }
}
