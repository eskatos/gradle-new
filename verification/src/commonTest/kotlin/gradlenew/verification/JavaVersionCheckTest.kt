package gradlenew.verification

import gradlenew.substitution.Placeholders
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaVersionCheckTest {

    private val model = model()
    private val sample = Sampling.entries(model)
    private val probed = mutableListOf<Pair<String, Int?>>()

    @Test
    fun classifiesTypesWhoseOutputDiffersOnlyInTheToolchainLiteralAsSubstituted() {
        val generations = Generations { spec, inputs ->
            probed += spec.type to inputs.javaVersion
            InitOutcome.Generated(fakeInit(inputs, spec.type))
        }

        val detection = JavaVersionCheck.run(model, sample, generations)

        assertEquals(CheckResult.of(Check.JAVA_VERSION, 2, emptyList()), detection.result)
        assertEquals(emptyList(), detection.enumeratedTypes)
        assertEquals(listOf("java-application" to JavaVersionCheck.PROBE_VERSION, "groovy-library" to JavaVersionCheck.PROBE_VERSION), probed.filter { it.second == JavaVersionCheck.PROBE_VERSION })
        assertEquals(setOf("java-application", "groovy-library"), probed.map { it.first }.toSet())
    }

    @Test
    fun classifiesATypeThatUsesTheJavaVersionElsewhereAsEnumerated() {
        val generations = Generations { spec, inputs ->
            val comment = if (spec.type == "groovy-library") listOf(text("lib/build.gradle", "// targets Java ${inputs.javaVersion}")) else emptyList()
            InitOutcome.Generated(fakeInit(inputs, spec.type, comment))
        }

        val detection = JavaVersionCheck.run(model, sample, generations)

        assertEquals(Outcome.PASSED, detection.result.outcome)
        assertEquals(listOf("groovy-library"), detection.enumeratedTypes)
    }

    @Test
    fun probesWithAVersionDifferentFromThePlaceholder() {
        kotlin.test.assertNotEquals(Placeholders.JAVA_VERSION, JavaVersionCheck.PROBE_VERSION)
    }

    @Test
    fun leavesRejectedProbesToTheModelCheck() {
        val generations = Generations { spec, inputs ->
            if (inputs.javaVersion == JavaVersionCheck.PROBE_VERSION && spec.type == "java-application") InitOutcome.Rejected("too new")
            else InitOutcome.Generated(fakeInit(inputs, spec.type))
        }

        val detection = JavaVersionCheck.run(model, sample, generations)

        assertEquals(CheckResult.of(Check.JAVA_VERSION, 1, emptyList()), detection.result)
        assertEquals(1, generations.rejections.size)
    }
}
