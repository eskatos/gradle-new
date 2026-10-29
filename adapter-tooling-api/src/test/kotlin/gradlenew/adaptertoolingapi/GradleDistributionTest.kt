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
    fun runsAGivenDistributionInsteadOfDownloadingByVersion() {
        val distribution = java.net.URI("https://services.gradle.org/distributions/gradle-${Pinned.stable}-bin.zip")
        val gradle = GradleDistribution(Pinned.stable, distribution = distribution)
        assertEquals(distribution, gradle.distribution)
        assertTrue(gradle.runTasks(emptyBuild(), listOf("--version", "--quiet")).contains("Gradle ${Pinned.stable}"))
    }

    @Test
    fun everyBuildCarriesTheDaemonIdleTimeout() {
        val build = emptyBuild()
        val initScript = build.resolve("print-idle-timeout.gradle.kts").apply {
            writeText("println(\"idle-timeout=\" + System.getProperty(\"org.gradle.daemon.idletimeout\"))\n")
        }
        val output = GradleDistribution(Pinned.stable).runTasks(build, listOf("--init-script", initScript.absolutePath, "help", "--offline"))
        assertTrue(output.contains("idle-timeout=${GradleDistribution.DAEMON_IDLE_TIMEOUT.inWholeMilliseconds}"), output)
    }

    @Test
    fun passesExtraArgumentsToEveryBuild() {
        val build = emptyBuild()
        val initScript = build.resolve("marker.gradle").apply { writeText("println 'extra-arguments-applied'\n") }
        val gradle = GradleDistribution(Pinned.stable, listOf("--init-script", initScript.absolutePath))

        assertTrue(gradle.runTasks(build, listOf("help", "--offline")).contains("extra-arguments-applied"))
        assertEquals(listOf("--init-script", initScript.absolutePath), gradle.extraArguments)
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
