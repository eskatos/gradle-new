package gradlenew.verification

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelCheckTest {

    @Test
    fun passesForAModelWhoseBuildTypesAllHaveCatalogEntries() {
        val model = model()

        assertEquals(CheckResult.of(Check.MODEL, model.buildTypes.size, emptyList()), ModelCheck.run(model, emptyList()))
    }

    @Test
    fun failsForAModelWithoutBuildTypes() {
        val result = ModelCheck.run(model(*emptyArray()), emptyList())

        assertEquals(listOf(Failure("9.8.2", "the model offers no build types")), result.failures)
    }

    @Test
    fun reportsEveryEntryInitRejectedWithInitsReason() {
        val rejections = listOf(Failure("basic.kotlin.none.single-project.no-incubating.comments", "gradle init rejected the entry: The requested build type 'basic' is not supported."))

        val result = ModelCheck.run(model(), rejections)

        assertEquals(Outcome.FAILED, result.outcome)
        assertEquals(rejections, result.failures)
    }

    @Test
    fun reportsAModelThatCouldNotBeObtained() {
        val result = ModelCheck.unavailable("9.9.0-rc-1", "builder-plugin did not compile: cannot find symbol ProjectLayoutSetupRegistry")

        assertEquals(CheckResult.of(Check.MODEL, 0, listOf(Failure("9.9.0-rc-1", "the model could not be obtained: builder-plugin did not compile: cannot find symbol ProjectLayoutSetupRegistry"))), result)
    }
}
