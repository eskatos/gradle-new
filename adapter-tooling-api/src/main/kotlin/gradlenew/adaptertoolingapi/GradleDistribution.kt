package gradlenew.adaptertoolingapi

import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

class GradleBuildFailed(val output: String, cause: Throwable) : RuntimeException("Gradle build failed:\n$output", cause)

class GradleDistribution(val version: String) {

    fun <T> connect(projectDir: File, action: (ProjectConnection) -> T): T {
        val connection = GradleConnector.newConnector()
            .useGradleVersion(version)
            .forProjectDirectory(projectDir)
            .connect()
        return connection.use(action)
    }

    fun runTasks(projectDir: File, arguments: List<String>): String {
        val output = ByteArrayOutputStream()
        return connect(projectDir) { connection ->
            try {
                connection.newBuild()
                    .withArguments(arguments)
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .run()
                output.toString(Charsets.UTF_8)
            } catch (failure: BuildException) {
                throw GradleBuildFailed(output.toString(Charsets.UTF_8), failure)
            }
        }
    }
}
