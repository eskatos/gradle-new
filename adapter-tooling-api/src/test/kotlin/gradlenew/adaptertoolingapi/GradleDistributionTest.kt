package gradlenew.adaptertoolingapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GradleDistributionTest {

    @Test
    fun runsTasksAgainstTheRequestedVersionAndCapturesOutput() {
        for (version in Pinned.versions) {
            val output = GradleDistribution(version).runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline"))
            assertTrue(output.contains("--type"), output)
            assertTrue(output.contains("java-application"), output)
        }
    }

    @Test
    fun reportsTheGradleVersionItRuns() {
        val output = GradleDistribution(Pinned.stable).runTasks(emptyBuild(), listOf("--version", "--quiet"))
        assertTrue(output.contains("Gradle ${Pinned.stable}"), output)
    }

    @Test
    fun aFailingBuildRaisesWithItsOutput() {
        val failure = assertFailsWith<GradleBuildFailed> {
            GradleDistribution(Pinned.stable).runTasks(emptyBuild(), listOf("noSuchTask", "--quiet"))
        }
        assertTrue(failure.output.contains("noSuchTask"), failure.output)
        assertEquals(Pinned.stable, GradleDistribution(Pinned.stable).version)
    }
}
