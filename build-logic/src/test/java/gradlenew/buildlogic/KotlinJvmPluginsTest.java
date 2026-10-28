package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KotlinJvmPluginsTest {

    @Test
    void libraryBuildsAndRunsJUnitPlatformTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("src/main/kotlin/probe/Greeting.kt", "package probe\n\nfun greeting(name: String) = \"Hello, $name\"\n")
            .file("src/test/kotlin/probe/GreetingTest.kt", """
                package probe

                import kotlin.test.Test
                import kotlin.test.assertEquals

                class GreetingTest {
                    @Test
                    fun greets() = assertEquals("Hello, Gradle", greeting("Gradle"))
                }
                """);

        BuildResult result = project.run("build");

        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
    }

    @Test
    void testsOfEveryJvmModuleSeeThePinnedGradleVersions(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("src/test/kotlin/probe/PinnedTest.kt", """
                package probe

                import kotlin.test.Test
                import kotlin.test.assertEquals

                class PinnedTest {
                    @Test
                    fun seesPinnedVersions() {
                        assertEquals("9.8.2", System.getProperty("gradlenew.offered.stable"))
                        assertEquals("9.9.0-rc-1", System.getProperty("gradlenew.offered.rc"))
                    }
                }
                """);

        BuildResult result = project.run("test", "-Pgradlenew.offered.stable=9.8.2", "-Pgradlenew.offered.rc=9.9.0-rc-1");

        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
    }

    @Test
    void applicationDerivesMainClassFromProjectName(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"my-tool\")\n")
            .file("my-tool/build.gradle.kts", "plugins { id(\"gradlenew.kotlin-jvm-application\") }")
            .file("my-tool/src/main/kotlin/gradlenew/mytool/Main.kt", """
                package gradlenew.mytool

                fun main() {
                    println("main class derived")
                }
                """);

        BuildResult result = project.run(":my-tool:run", "-q");

        assertTrue(result.getOutput().contains("main class derived"));
    }
}
