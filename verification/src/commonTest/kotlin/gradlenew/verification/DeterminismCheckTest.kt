package gradlenew.verification

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterminismCheckTest {

    private val model = model()
    private val sample = Sampling.entries(model)

    @Test
    fun passesWhenEverySampledEntryIsGeneratedIdentically() {
        val generations = Generations { spec, inputs -> InitOutcome.Generated(fakeInit(inputs, spec.type)) }

        val result = DeterminismCheck.run(sample, generations)

        assertEquals(CheckResult.of(Check.DETERMINISM, sample.size, emptyList()), result)
    }

    @Test
    fun namesTheTemplateAndTheFileThatDiffers() {
        var run = 0
        val generations = Generations { spec, inputs ->
            InitOutcome.Generated(fakeInit(inputs, spec.type, listOf(text("generated-at.txt", "${run++}"))))
        }

        val result = DeterminismCheck.run(sample, generations)

        assertEquals(Outcome.FAILED, result.outcome)
        assertEquals(sample.map { it.spec.key }, result.failures.map { it.subject })
        assertTrue(result.failures.all { it.message == "generated-at.txt: content differs" }, result.toString())
    }

    @Test
    fun detectsAnExecutableBitThatChanges() {
        var executable = false
        val generations = Generations { spec, inputs ->
            executable = !executable
            InitOutcome.Generated(FileSet.of(FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), executable), text("settings.gradle.kts", spec.type)))
        }

        val result = DeterminismCheck.run(sample.take(1), generations)

        assertEquals(listOf(Failure(sample.first().spec.key, "gradlew: executable false, expected true")), result.failures)
    }

    @Test
    fun leavesRejectedEntriesToTheModelCheck() {
        val generations = Generations { spec, inputs ->
            if (spec.type == "basic") InitOutcome.Rejected("no basic today") else InitOutcome.Generated(fakeInit(inputs, spec.type))
        }

        val result = DeterminismCheck.run(sample, generations)

        assertEquals(CheckResult.of(Check.DETERMINISM, sample.size - 1, emptyList()), result)
        assertEquals(listOf(Failure(sample.last().spec.key, "gradle init rejected the entry: no basic today")), generations.rejections)
    }

    @Test
    fun generatesASampledTemplateOnceAcrossChecks() {
        val calls = mutableListOf<String>()
        val generations = Generations { spec, inputs -> calls += spec.key; InitOutcome.Generated(fakeInit(inputs, spec.type)) }
        val entry = sample.first()

        generations.template(entry)
        generations.template(entry)
        generations.fresh(entry.spec, entry.placeholders)

        assertEquals(listOf(entry.spec.key, entry.spec.key), calls)
    }
}
