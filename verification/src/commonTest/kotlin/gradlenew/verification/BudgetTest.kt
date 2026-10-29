package gradlenew.verification

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.substitution.FileSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetTest {

    private val model = model()
    private val catalog = TemplateCatalog.of(model)
    private val written = mutableListOf<CatalogEntry>()
    private val sink = TemplateSink { entry, files -> written += entry; size(files) }

    private fun size(files: FileSet) = files.entries.sumOf { it.content.size }.toLong()

    private fun verification(budgetBytes: Long, extra: (CatalogEntry) -> Int = { 0 }) = Verification(
        InitGenerator { spec, inputs ->
            val entry = catalog.firstOrNull { it.spec == spec && it.placeholders == inputs }
            InitOutcome.Generated(fakeInit(inputs, spec.type, listOf(text("padding.txt", "x".repeat(entry?.let(extra) ?: 0)))))
        },
        sink,
        budgetBytes,
        sizeOf = ::size,
    )

    @Test
    fun failsBeforeTheMatrixWhenTheProjectedSizeIsOverTheBudget() {
        val report = verification(budgetBytes = 100).verify(model)

        val budget = report.check(Check.BUDGET)!!
        assertEquals(Outcome.FAILED, budget.outcome)
        assertEquals(catalog.size, budget.checked)
        assertTrue(budget.failures.single().message.startsWith("${catalog.size} templates would take about "), budget.toString())
        assertTrue(budget.failures.single().message.endsWith(" bytes, over the budget of 100 bytes"), budget.toString())
        assertEquals(emptyList(), written)
    }

    @Test
    fun failsAfterTheMatrixWhenTheActualSizeIsOverTheBudget() {
        val projected = Sampling.entries(model).sumOf { sample -> catalog.count { it.spec.type == sample.spec.type } * size(fakeInit(sample.placeholders, sample.spec.type, listOf(text("padding.txt", "")))) }
        val report = verification(budgetBytes = projected + 10) { if (it.spec.incubating) 1_000 else 0 }.verify(model)

        val budget = report.check(Check.BUDGET)!!
        assertEquals(Outcome.FAILED, budget.outcome, budget.toString())
        assertTrue(budget.failures.single().message.startsWith("${catalog.size} templates take "), budget.toString())
        assertEquals(catalog.size, written.size)
    }

    @Test
    fun passesWithinTheBudget() {
        assertEquals(Outcome.PASSED, verification(budgetBytes = Long.MAX_VALUE).verify(model).check(Check.BUDGET)!!.outcome)
    }
}
