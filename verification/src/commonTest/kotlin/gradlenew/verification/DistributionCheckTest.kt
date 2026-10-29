package gradlenew.verification

import kotlin.test.Test
import kotlin.test.assertEquals

class DistributionCheckTest {

    @Test
    fun passesWhenTheDigestsMatch() {
        assertEquals(CheckResult.of(Check.DISTRIBUTION, 1, emptyList()), DistributionCheck.of("9.8.2", "a".repeat(64), "a".repeat(64)))
    }

    @Test
    fun namesBothDigestsOnAMismatch() {
        val result = DistributionCheck.of("9.8.2", "a".repeat(64), "b".repeat(64))
        assertEquals(Outcome.FAILED, result.outcome)
        assertEquals(
            listOf(Failure("gradle-9.8.2-bin.zip", "SHA-256 is ${"b".repeat(64)}, expected ${"a".repeat(64)}")),
            result.failures,
        )
    }

    @Test
    fun reportsADistributionThatCouldNotBeObtained() {
        assertEquals(
            listOf(Failure("gradle-9.8.2-bin.zip", "the distribution could not be obtained: offline")),
            DistributionCheck.unavailable("9.8.2", "offline").failures,
        )
    }
}
