package gradlenew.adaptertoolingapi

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.tooling.model.build.BuildEnvironment

class GradleDistributionTest {

    @Test
    fun runsTasksAgainstTheRequestedVersionAndCapturesOutput() {
        for (version in Pinned.versions) {
            val output = Pinned.distribution(version).runTasks(emptyBuild(), listOf("help", "--task", "init", "--quiet", "--offline"))
            assertTrue(output.contains("--type"), output)
            assertTrue(output.contains("java-application"), output)
        }
    }

    @Test
    fun runsTheDaemonOnTheJavaItIsGiven() {
        val javaHome = File(System.getProperty("java.home")).canonicalFile
        val gradle = GradleDistribution(Pinned.stable, javaHome = javaHome)
        assertEquals(javaHome, gradle.javaHome)
        val environment = gradle.requestModel(emptyBuild(), BuildEnvironment::class.java, listOf("--offline"))
        assertEquals(javaHome, environment.java.javaHome.canonicalFile)
    }

    @Test
    fun everyOfferedVersionRunsOnTheJavaItsSlotPins() {
        for (version in Pinned.versions) {
            val javaHome = requireNotNull(Pinned.javaHome(version)) { "the build gives no Java home for Gradle $version" }
            val environment = Pinned.distribution(version).requestModel(emptyBuild(), BuildEnvironment::class.java, listOf("--offline"))
            assertEquals(javaHome.canonicalFile, environment.java.javaHome.canonicalFile, version)
        }
    }

    @Test
    fun refusesAJavaHomeThatIsNotAJava() {
        val notAJava = Files.createTempDirectory("not-a-java").toFile()
        assertFails { GradleDistribution(Pinned.stable, javaHome = notAJava).runTasks(emptyBuild(), listOf("help", "--quiet", "--offline")) }
    }

    @Test
    fun reportsTheGradleVersionItRuns() {
        val output = Pinned.distribution(Pinned.stable).runTasks(emptyBuild(), listOf("--version", "--quiet"))
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
        val output = Pinned.distribution(Pinned.stable).runTasks(build, listOf("--init-script", initScript.absolutePath, "help", "--offline"))
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
            Pinned.distribution(Pinned.stable).runTasks(emptyBuild(), listOf("noSuchTask", "--quiet"))
        }
        assertTrue(failure.output.contains("noSuchTask"), failure.output)
        assertEquals(Pinned.stable, Pinned.distribution(Pinned.stable).version)
    }
}
