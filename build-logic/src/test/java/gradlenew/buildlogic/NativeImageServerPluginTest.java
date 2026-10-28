package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeImageServerPluginTest {

    @Test
    void registersNativeImageAndContainerImageTasks(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.native-image-server\") }");

        BuildResult result = project.run("tasks", "--all", "-q");

        assertTrue(result.getOutput().contains("nativeCompile"));
        assertTrue(result.getOutput().contains("buildImage"));
    }

    @Test
    void runsOnTheJvmWithTheSiteDirectory(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"ui\", \"server\")\n")
            .file("ui/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }")
            .file("ui/src/site/versions.json", "{\"stable\":\"9.8.2\"}")
            .file("server/build.gradle.kts", """
                plugins { id("gradlenew.native-image-server") }
                dependencies { site(project(":ui")) }
                """)
            .file("server/src/main/kotlin/gradlenew/server/Main.kt", """
                package gradlenew.server

                import java.io.File

                fun main() {
                    println("site has " + File(System.getenv("SITE_DIR"), "versions.json").readText())
                }
                """);

        BuildResult result = project.run(":server:run", "-q");

        assertTrue(result.getOutput().contains("site has {\"stable\":\"9.8.2\"}"));
    }
}
