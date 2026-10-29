package gradlenew.publisher

import gradlenew.versions.Checksums
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgumentsTest {

    private val required = listOf(
        "--version", "9.8.2", "--builder-dir", "b", "--catalog", "c.toml", "--output", "out",
        "--templates-output", "tpl", "--report-output", "report", "--work-dir", "work", "--distribution-cache", "dists",
    )

    @Test
    fun parsesAllOptions() {
        val arguments = Arguments.parse(
            required + listOf(
                "--template-budget-mb", "12", "--gradle-argument", "--init-script", "--gradle-argument", "fault.gradle",
                "--distribution-sha256", "a".repeat(64), "--wrapper-sha256", "b".repeat(64), "--distributions-url", "http://localhost:1/d/",
            ),
        )

        assertEquals(
            Arguments(
                version = "9.8.2", builderSources = File("b"), versionCatalog = File("c.toml"),
                output = File("out"), templatesOutput = File("tpl"), reportOutput = File("report"), workDir = File("work"),
                templateBudgetBytes = 12L * 1024 * 1024, gradleArguments = listOf("--init-script", "fault.gradle"),
                checksums = Checksums("a".repeat(64), "b".repeat(64)), distributionCache = File("dists"), distributionsUrl = URI("http://localhost:1/d/"),
            ),
            arguments,
        )
    }

    @Test
    fun defaultsTheBudgetAndHasNoExtraGradleArguments() {
        val arguments = Arguments.parse(required)

        assertEquals(64L * 1024 * 1024, arguments.templateBudgetBytes)
        assertEquals(emptyList(), arguments.gradleArguments)
        assertEquals(null, arguments.checksums, "no pinned checksums means the published ones are used")
        assertEquals(Distributions.DEFAULT_BASE_URL, arguments.distributionsUrl)
    }

    @Test
    fun rejectsMissingRepeatedOrMalformedOptions() {
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required.drop(2)) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + "--work-dir") }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + listOf("--version", "9.8.1")) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + listOf("--template-budget-mb", "lots")) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + listOf("--distribution-sha256", "a".repeat(64))) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required.dropLast(2)) }
    }
}
