package gradlenew.adapterjson

import gradlenew.domain.generation.TemplateIndex
import gradlenew.verification.Check
import gradlenew.verification.CheckResult
import gradlenew.verification.ChecksumSource
import gradlenew.verification.Failure
import gradlenew.verification.VerificationReport
import gradlenew.verification.VersionReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationReportJsonTest {

    private val report = VerificationReport(
        listOf(
            VersionReport(
                "9.8.2",
                listOf(
                    CheckResult.of(Check.MODEL, 16, emptyList()),
                    CheckResult.of(Check.DETERMINISM, 16, listOf(Failure("java-application.kotlin.junit-jupiter.single-project.no-incubating.comments", "generated-at.txt: content differs"))),
                    CheckResult.skipped(Check.BUDGET),
                ),
                listOf("groovy-library"),
            ),
        ),
    )

    @Test
    fun encodesTheReport() {
        assertEquals(
            """
            {
                "passed": false,
                "versions": [
                    {
                        "version": "9.8.2",
                        "passed": false,
                        "checksums": "pinned",
                        "javaEnumeratedTypes": [
                            "groovy-library"
                        ],
                        "checks": [
                            {
                                "check": "model",
                                "outcome": "passed",
                                "checked": 16,
                                "failures": []
                            },
                            {
                                "check": "determinism",
                                "outcome": "failed",
                                "checked": 16,
                                "failures": [
                                    {
                                        "subject": "java-application.kotlin.junit-jupiter.single-project.no-incubating.comments",
                                        "message": "generated-at.txt: content differs"
                                    }
                                ]
                            },
                            {
                                "check": "budget",
                                "outcome": "skipped",
                                "checked": 0,
                                "failures": []
                            }
                        ]
                    }
                ]
            }
            """.trimIndent(),
            VerificationReportJson.encode(report),
        )
    }

    @Test
    fun roundTrips() {
        assertEquals(report, VerificationReportJson.decode(VerificationReportJson.encode(report)))
        val published = VerificationReport(listOf(VersionReport("9.8.0-rc-3", listOf(CheckResult.of(Check.DISTRIBUTION, 1, emptyList()), CheckResult.of(Check.WRAPPER, 224, emptyList())), emptyList(), ChecksumSource.PUBLISHED)))
        val encoded = VerificationReportJson.encode(published)
        assertEquals(published, VerificationReportJson.decode(encoded))
        assert("\"checksums\": \"published\"" in encoded) { encoded }
    }

    @Test
    fun rejectsAnUnknownCheck() {
        val text = VerificationReportJson.encode(report).replace("\"determinism\"", "\"astrology\"")

        assertFailsWith<IllegalArgumentException> { VerificationReportJson.decode(text) }
    }

    @Test
    fun encodesTheTemplateIndexWithSortedTypes() {
        val index = TemplateIndex(setOf("scala-library", "groovy-library"))

        assertEquals("{\n    \"javaEnumeratedTypes\": [\n        \"groovy-library\",\n        \"scala-library\"\n    ]\n}", TemplateIndexJson.encode(index))
        assertEquals(index, TemplateIndexJson.decode(TemplateIndexJson.encode(index)))
        assertEquals(TemplateIndex.NONE, TemplateIndexJson.decode("{\"javaEnumeratedTypes\": []}"))
    }
}
