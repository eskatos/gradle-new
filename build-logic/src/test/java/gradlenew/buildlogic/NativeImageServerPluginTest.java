package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeImageServerPluginTest {

    @Test
    void registersImageTasksAndNoHostNativeCompilation(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.native-image-server\") }");

        BuildResult result = project.run("tasks", "--all", "-q");

        assertTrue(result.getOutput().contains("assembleImageContext"));
        assertTrue(result.getOutput().contains("buildImage"));
        assertFalse(result.getOutput().contains("nativeCompile"));
    }

    @Test
    void assemblesTheImageContextFromJarsDockerfileAndSite(@TempDir Path dir) throws IOException {
        TestProject project = serverWithSite(dir)
            .file("server/Dockerfile", "FROM scratch\nARG MAIN_CLASS\nCOPY lib lib\nCOPY site /site\n");

        project.run(":server:assembleImageContext", "-q");

        Path context = dir.resolve("server/build/image/context");
        assertTrue(Files.exists(context.resolve("Dockerfile")));
        assertTrue(Files.exists(context.resolve("lib/server.jar")));
        try (var jars = Files.list(context.resolve("lib"))) {
            assertTrue(jars.anyMatch(jar -> jar.getFileName().toString().startsWith("kotlin-stdlib-")));
        }
        assertTrue(Files.exists(context.resolve("site/versions.json")));
        assertFalse(Files.exists(context.resolve("bin")));
    }

    @Test
    void runsOnTheJvmWithTheSiteDirectory(@TempDir Path dir) throws IOException {
        TestProject project = serverWithSite(dir);

        BuildResult result = project.run(":server:run", "-q");

        assertTrue(result.getOutput().contains("site has {\"stable\":\"9.8.2\"}"));
    }

    private static TestProject serverWithSite(Path dir) throws IOException {
        return TestProject.in(dir)
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
    }
}
