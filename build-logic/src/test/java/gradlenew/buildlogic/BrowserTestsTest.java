package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserTestsTest {

    @Test
    void browserTestTasksOfDifferentProjectsNeverRunAtTheSameTime(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"ui\", \"acceptance\")\n")
            .file("ui/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }")
            .file("ui/src/site/index.html", "<!doctype html><title>site</title>")
            .file("ui/src/agent/SKILL.md", "---\nname: test\n---\n")
            .file("acceptance/build.gradle.kts", "plugins { id(\"gradlenew.acceptance-tests\") }");
        for (String module : List.of("ui", "acceptance")) {
            project.file(module + "/src/test/kotlin/BrowserTest.kt", """
                import java.io.File
                import kotlin.test.Test

                class BrowserTest {
                    @Test
                    fun recordsWhenItRuns() {
                        val start = System.currentTimeMillis()
                        Thread.sleep(3000)
                        File("run.txt").writeText("$start ${System.currentTimeMillis()}")
                    }
                }
                """);
        }

        BuildResult result = project.run(":ui:test", ":acceptance:test", "--parallel", "--max-workers=4",
            "-x", ":ui:installPlaywrightBrowsers", "-x", ":acceptance:installPlaywrightBrowsers");

        long[] ui = interval(project.path("ui/run.txt"));
        long[] acceptance = interval(project.path("acceptance/run.txt"));
        assertTrue(ui[1] <= acceptance[0] || acceptance[1] <= ui[0],
            "ui ran " + ui[0] + ".." + ui[1] + ", acceptance " + acceptance[0] + ".." + acceptance[1] + "\n" + result.getOutput());
    }

    @Test
    void playwrightInstallersOfDifferentProjectsNeverRunAtTheSameTime(@TempDir Path dir) throws IOException {
        String standIn = """
            tasks.named<JavaExec>("installPlaywrightBrowsers") {
                classpath = files(sourceSets.test.map { it.output }) + configurations.testRuntimeClasspath.get()
                mainClass = "InstallKt"
                dependsOn("testClasses")
            }
            """;
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"ui\", \"acceptance\")\n")
            .file("ui/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }\n" + standIn)
            .file("ui/src/site/index.html", "<!doctype html><title>site</title>")
            .file("ui/src/agent/SKILL.md", "---\nname: test\n---\n")
            .file("acceptance/build.gradle.kts", "plugins { id(\"gradlenew.acceptance-tests\") }\n" + standIn);
        for (String module : List.of("ui", "acceptance")) {
            project.file(module + "/src/test/kotlin/Install.kt", """
                import java.io.File

                fun main() {
                    val start = System.currentTimeMillis()
                    Thread.sleep(3000)
                    File("install.txt").writeText("$start ${System.currentTimeMillis()}")
                }
                """);
        }

        BuildResult result = project.run(":ui:installPlaywrightBrowsers", ":acceptance:installPlaywrightBrowsers", "--parallel", "--max-workers=4");

        long[] ui = interval(project.path("ui/install.txt"));
        long[] acceptance = interval(project.path("acceptance/install.txt"));
        assertTrue(ui[1] <= acceptance[0] || acceptance[1] <= ui[0],
            "ui installed " + ui[0] + ".." + ui[1] + ", acceptance " + acceptance[0] + ".." + acceptance[1] + "\n" + result.getOutput());
    }

    private static long[] interval(Path file) throws IOException {
        String[] parts = Files.readString(file).split(" ");
        return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }
}
