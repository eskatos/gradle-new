package gradlenew.adaptertoolingapi

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

class GradleBuildFailed(val output: String, cause: Throwable) : RuntimeException("Gradle build failed:\n$output", cause)

class GradleDistribution(val version: String) {

    fun <T> connect(projectDir: File, searchUpwards: Boolean = true, action: (ProjectConnection) -> T): T {
        val connector = GradleConnector.newConnector()
            .useGradleVersion(version)
            .forProjectDirectory(projectDir)
        (connector as? DefaultGradleConnector)?.apply {
            daemonMaxIdleTime(DAEMON_IDLE_MINUTES, TimeUnit.MINUTES)
            searchUpwards(searchUpwards)
        }
        return connector.connect().use(action)
    }

    fun runTasks(projectDir: File, arguments: List<String>, searchUpwards: Boolean = true): String {
        val output = ByteArrayOutputStream()
        return connect(projectDir, searchUpwards) { connection ->
            try {
                connection.newBuild()
                    .withArguments(arguments)
                    .setJvmArguments(DAEMON_JVM_ARGUMENTS)
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .run()
                output.toString(Charsets.UTF_8)
            } catch (failure: BuildException) {
                throw GradleBuildFailed(output.toString(Charsets.UTF_8), failure)
            }
        }
    }

    companion object {
        private const val DAEMON_IDLE_MINUTES = 2
        private val DAEMON_JVM_ARGUMENTS = listOf("-Xmx256m", "-XX:MaxMetaspaceSize=384m")
    }
}
