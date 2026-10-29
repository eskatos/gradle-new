package gradlenew.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {

    private fun allPassed(version: String) = VersionReport(version, Check.entries.map { CheckResult.of(it, checked = 3, failures = emptyList()) }, emptyList())

    @Test
    fun aCheckFailsExactlyWhenItHasFailures() {
        assertEquals(Outcome.PASSED, CheckResult.of(Check.DETERMINISM, 16, emptyList()).outcome)
        assertEquals(Outcome.FAILED, CheckResult.of(Check.DETERMINISM, 16, listOf(Failure("java-application.kotlin", "a.txt: content differs"))).outcome)
        assertEquals(CheckResult(Check.HYGIENE, Outcome.SKIPPED, 0, emptyList()), CheckResult.skipped(Check.HYGIENE))
        assertFailsWith<IllegalArgumentException> { CheckResult(Check.MODEL, Outcome.PASSED, 1, listOf(Failure("x", "y"))) }
        assertFailsWith<IllegalArgumentException> { CheckResult(Check.MODEL, Outcome.FAILED, 1, emptyList()) }
    }

    @Test
    fun aVersionPassesOnlyWhenEveryCheckPassed() {
        assertTrue(allPassed("9.8.2").passed)
        val withSkipped = VersionReport("9.8.2", listOf(CheckResult.of(Check.MODEL, 1, emptyList()), CheckResult.skipped(Check.DETERMINISM)), emptyList())
        assertFalse(withSkipped.passed)
        val withFailure = VersionReport("9.8.2", listOf(CheckResult.of(Check.MODEL, 1, listOf(Failure("basic", "no catalog entries")))), emptyList())
        assertFalse(withFailure.passed)
        assertFailsWith<IllegalArgumentException> { VersionReport("9.8.2", listOf(CheckResult.skipped(Check.MODEL), CheckResult.skipped(Check.MODEL)), emptyList()) }
    }

    @Test
    fun aggregatesPerVersion() {
        val failed = VersionReport("9.9.0-rc-1", listOf(CheckResult.of(Check.MODEL, 1, listOf(Failure("9.9.0-rc-1", "builder did not compile")))), emptyList())
        val report = VerificationReport(listOf(allPassed("9.8.2"), failed))

        assertFalse(report.passed)
        assertEquals(listOf("9.8.2"), report.passedVersions)
        assertEquals(listOf("9.9.0-rc-1"), report.failedVersions)
        assertEquals(Outcome.FAILED, report.version("9.9.0-rc-1").check(Check.MODEL)?.outcome)
        assertEquals(null, failed.check(Check.BUDGET))
    }

    @Test
    fun summarisesChecksFailuresAndEnumeratedTypes() {
        val failures = (1..7).map { Failure("type-$it.kotlin", "generated-at.txt: content differs") }
        val report = VerificationReport(
            listOf(
                VersionReport(
                    "9.8.2",
                    listOf(
                        CheckResult.of(Check.MODEL, 16, emptyList()),
                        CheckResult.of(Check.DETERMINISM, 16, failures),
                        CheckResult.skipped(Check.HYGIENE),
                    ),
                    listOf("groovy-library"),
                ),
            ),
        )

        val summary = report.summary()

        assertEquals(
            """
            Gradle 9.8.2: FAILED
              model: passed (16 checked)
              determinism: FAILED (16 checked, 7 failures)
                type-1.kotlin: generated-at.txt: content differs
                type-2.kotlin: generated-at.txt: content differs
                type-3.kotlin: generated-at.txt: content differs
                type-4.kotlin: generated-at.txt: content differs
                type-5.kotlin: generated-at.txt: content differs
                and 2 more
              hygiene: skipped
              Java version enumerated for: groovy-library
            """.trimIndent() + "\n",
            summary,
        )
    }

    @Test
    fun checksHaveStableIdentifiers() {
        assertEquals(listOf("model", "determinism", "java-version", "differential", "hygiene", "budget"), Check.entries.map { it.id })
        assertEquals(Check.JAVA_VERSION, Check.of("java-version"))
    }
}
