package gradlenew.publisher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArgumentsTest {

    private val required = listOf(
        "--stable", "9.8.2", "--builder-dir", "b", "--catalog", "c.toml", "--output", "out",
        "--templates-output", "tpl", "--report-output", "report", "--work-dir", "work",
    )

    @Test
    fun parsesAllOptions() {
        val arguments = Arguments.parse(
            required + listOf("--rc", "9.9.0-rc-1", "--template-budget-mb", "12", "--gradle-argument", "--init-script", "--gradle-argument", "fault.gradle"),
        )

        assertEquals(
            Arguments(
                stable = "9.8.2", rc = "9.9.0-rc-1", builderSources = File("b"), versionCatalog = File("c.toml"),
                output = File("out"), templatesOutput = File("tpl"), reportOutput = File("report"), workDir = File("work"),
                templateBudgetBytes = 12L * 1024 * 1024, gradleArguments = listOf("--init-script", "fault.gradle"),
            ),
            arguments,
        )
    }

    @Test
    fun defaultsTheBudgetAndHasNoExtraGradleArguments() {
        val arguments = Arguments.parse(required)

        assertEquals(64L * 1024 * 1024, arguments.templateBudgetBytes)
        assertEquals(emptyList(), arguments.gradleArguments)
    }

    @Test
    fun rcIsOptionalAndBlankMeansAbsent() {
        assertNull(Arguments.parse(required).rc)
        assertNull(Arguments.parse(required + listOf("--rc", "")).rc)
    }

    @Test
    fun rejectsMissingRepeatedOrMalformedOptions() {
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required.drop(2)) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + "--rc") }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + listOf("--stable", "9.8.1")) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + listOf("--template-budget-mb", "lots")) }
    }
}
