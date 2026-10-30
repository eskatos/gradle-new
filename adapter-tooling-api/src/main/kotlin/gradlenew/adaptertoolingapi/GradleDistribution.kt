package gradlenew.adaptertoolingapi

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.minutes
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

class GradleBuildFailed(val output: String, cause: Throwable) : RuntimeException("Gradle build failed:\n$output", cause)

class GradleDistribution(
    val version: String,
    val extraArguments: List<String> = emptyList(),
    val distribution: URI? = null,
    val javaHome: File? = null,
) {

    fun runTasks(projectDir: File, arguments: List<String>): String {
        val output = ByteArrayOutputStream()
        return connect(projectDir) { connection ->
            try {
                connection.newBuild()
                    .withArguments(DAEMON_ARGUMENTS + extraArguments + arguments)
                    .setJvmArguments(DAEMON_JVM_ARGUMENTS)
                    .setJavaHome(javaHome)
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .run()
                output.toString(Charsets.UTF_8)
            } catch (failure: BuildException) {
                throw GradleBuildFailed(output.toString(Charsets.UTF_8), failure)
            }
        }
    }

    fun <T> requestModel(projectDir: File, type: Class<T>, arguments: List<String>): T =
        connect(projectDir) { connection ->
            connection.model(type)
                .withArguments(DAEMON_ARGUMENTS + extraArguments + arguments)
                .setJvmArguments(DAEMON_JVM_ARGUMENTS)
                .setJavaHome(javaHome)
                .get()
        }

    private fun <T> connect(projectDir: File, action: (ProjectConnection) -> T): T =
        GradleConnector.newConnector()
            .let { if (distribution != null) it.useDistribution(distribution) else it.useGradleVersion(version) }
            .forProjectDirectory(projectDir)
            .connect()
            .use(action)

    companion object {
        val DAEMON_IDLE_TIMEOUT = 2.minutes
        private val DAEMON_ARGUMENTS = listOf("-Dorg.gradle.daemon.idletimeout=${DAEMON_IDLE_TIMEOUT.inWholeMilliseconds}")
        private val DAEMON_JVM_ARGUMENTS = listOf("-Xmx256m", "-XX:MaxMetaspaceSize=384m")
    }
}
