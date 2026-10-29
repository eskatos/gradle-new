package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.generation.TemplateSpec
import gradlenew.substitution.FileSet
import gradlenew.substitution.ProjectInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationTest {

    private val model = model()
    private val written = mutableListOf<CatalogEntry>()
    private val sink = TemplateSink { entry, files -> written += entry; files.entries.sumOf { it.content.size }.toLong() }

    private fun verification(init: (TemplateSpec, ProjectInputs) -> FileSet) =
        Verification(InitGenerator { spec, inputs -> InitOutcome.Generated(init(spec, inputs)) }, sink, budgetBytes = Long.MAX_VALUE, wrapper = fakeWrapper)

    @Test
    fun passesEveryCheckAndWritesTheWholeMatrix() {
        val report = verification { spec, inputs -> fakeInit(inputs, spec.type) }.verify(model)

        assertTrue(report.passed, report.toString())
        assertEquals("9.8.2", report.version)
        assertEquals(Check.entries - Check.DISTRIBUTION, report.checks.map { it.check }, "the distribution is checked by the publisher before verification")
        assertEquals(written.size, report.check(Check.WRAPPER)!!.checked)
        assertEquals(TemplateCatalog.of(model), written)
        assertEquals(written.size, report.check(Check.HYGIENE)!!.checked)
        assertEquals(emptyList(), report.javaEnumeratedTypes)
    }

    @Test
    fun stopsBeforeTheMatrixWhenASampledCheckFails() {
        var run = 0
        val report = verification { spec, inputs -> fakeInit(inputs, spec.type, listOf(text("generated-at.txt", "${run++}"))) }.verify(model)

        assertFalse(report.passed)
        assertEquals(Outcome.FAILED, report.check(Check.DETERMINISM)!!.outcome)
        assertEquals(Outcome.PASSED, report.check(Check.HYGIENE)!!.outcome, report.check(Check.HYGIENE).toString())
        assertEquals(model.buildTypes.size, report.check(Check.HYGIENE)!!.checked)
        assertEquals(Outcome.SKIPPED, report.check(Check.BUDGET)!!.outcome)
        assertEquals(emptyList(), written)
    }

    @Test
    fun findsAFaultOutsideTheSampleWhileGeneratingTheMatrix() {
        val report = verification { spec, inputs ->
            fakeInit(inputs, spec.type, if (spec.incubating) listOf(text("NOTES.txt", inputs.projectName.replace('-', ' '))) else emptyList())
        }.verify(model)

        assertFalse(report.passed)
        assertEquals(Outcome.PASSED, report.check(Check.DIFFERENTIAL)!!.outcome)
        val hygiene = report.check(Check.HYGIENE)!!
        assertEquals(Outcome.FAILED, hygiene.outcome)
        assertTrue(hygiene.failures.isNotEmpty() && hygiene.failures.all { ".incubating." in it.subject }, hygiene.toString())
    }

    @Test
    fun reportsEntriesInitRejectsWhileGeneratingTheMatrixUnderTheModelCheck() {
        val rejecting = Verification(
            InitGenerator { spec, inputs ->
                if (spec.type == "basic" && spec.dsl == "groovy") InitOutcome.Rejected("groovy is gone") else InitOutcome.Generated(fakeInit(inputs, spec.type))
            },
            sink,
            budgetBytes = Long.MAX_VALUE,
            wrapper = fakeWrapper,
        )

        val report = rejecting.verify(model)

        val modelCheck = report.check(Check.MODEL)!!
        assertEquals(Outcome.FAILED, modelCheck.outcome)
        assertTrue(modelCheck.failures.all { it.subject.startsWith("basic.groovy.") && it.message.endsWith("groovy is gone") }, modelCheck.toString())
        assertFalse(written.any { it.spec.type == "basic" && it.spec.dsl == "groovy" })
    }

    @Test
    fun returnsTheJavaClassification() {
        val report = verification { spec, inputs ->
            fakeInit(inputs, spec.type, if (spec.type == "groovy-library") listOf(text("lib/build.gradle", "// targets Java ${inputs.javaVersion}")) else emptyList())
        }.verify(model)

        assertEquals(listOf("groovy-library"), report.javaEnumeratedTypes)
        assertTrue(report.passed, report.toString())
        assertEquals(TemplateCatalog.of(model, setOf("groovy-library")), written)
        assertEquals((7..31).toSet(), written.filter { it.spec.type == "groovy-library" }.map { it.spec.javaVersion }.toSet())
    }

    @Test
    fun aWrapperJarThatIsNotThePinnedOneFailsBeforeTheMatrix() {
        val report = verification { spec, inputs ->
            FileSet.of(fakeInit(inputs, spec.type).entries.map {
                if (it.path == WrapperCheck.PATH) gradlenew.substitution.FileEntry(it.path, it.content + 0x01, it.executable) else it
            })
        }.verify(model)

        assertFalse(report.passed)
        val wrapper = report.check(Check.WRAPPER)!!
        assertEquals(Outcome.FAILED, wrapper.outcome)
        assertEquals(model.buildTypes.size, wrapper.failures.size, "one failure per sampled build type")
        assertEquals(Outcome.SKIPPED, report.check(Check.BUDGET)!!.outcome)
        assertEquals(emptyList(), written)
    }
}
