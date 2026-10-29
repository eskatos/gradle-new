package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
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
    void testsOfALibraryThatOptsInSeeTheOfferedGradleVersions(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("""
                plugins { id("gradlenew.kotlin-jvm-library") }
                jvmLibrary { testsUseOfferedGradleVersions = true }
                """)
            .file("offered-gradle-versions.properties", "stable=9.8.2\nrc=9.9.0-rc-1\n")
            .file("src/test/kotlin/probe/PinnedTest.kt", PINNED_PROBE);

        BuildResult result = project.run("test");

        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
        assertEquals("9.8.2|9.9.0-rc-1", Files.readString(project.path("probe.txt")));
    }

    @Test
    void testsOfALibraryWithoutTheOptInNeitherSeeNorDependOnTheOfferedVersions(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n")
            .file("src/test/kotlin/probe/PinnedTest.kt", PINNED_PROBE);

        project.run("test");
        assertEquals("null|null", Files.readString(project.path("probe.txt")));

        project.file("offered-gradle-versions.properties", "stable=9.9.0\nrc=9.10.0-rc-1\n");
        assertEquals(TaskOutcome.UP_TO_DATE, project.run("test").task(":test").getOutcome());
    }

    private static final String PINNED_PROBE = """
        package probe

        import java.io.File
        import kotlin.test.Test

        class PinnedTest {
            @Test
            fun recordsOfferedVersions() {
                File("probe.txt").writeText(System.getProperty("gradlenew.offered.stable") + "|" + System.getProperty("gradlenew.offered.rc"))
            }
        }
        """;

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

    @Test
    void failsOnACompilerWarningInMainOrTestCode(@TempDir Path dir) throws IOException {
        String withWarning = """
            package probe

            @Deprecated("Use greeting")
            fun hello(name: String) = "Hello, $name"

            fun greeting(name: String) = hello(name)
            """;
        TestProject main = TestProject.in(dir.resolve("main"))
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("src/main/kotlin/probe/Greeting.kt", withWarning);
        assertTrue(main.runAndFail("compileKotlin").getOutput().contains("is deprecated. Use greeting"));

        TestProject test = TestProject.in(dir.resolve("test"))
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("src/test/kotlin/probe/Greeting.kt", withWarning);
        assertTrue(test.runAndFail("compileTestKotlin").getOutput().contains("is deprecated. Use greeting"));
    }

    @Test
    void testAndApplicationJvmsHaveNativeAccess(@TempDir Path dir) throws IOException {
        TestProject library = TestProject.in(dir.resolve("library"))
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-library\") }")
            .file("src/test/kotlin/probe/NativeAccessTest.kt", """
                package probe

                import java.lang.management.ManagementFactory
                import kotlin.test.Test
                import kotlin.test.assertTrue

                class NativeAccessTest {
                    @Test
                    fun hasNativeAccess() {
                        val arguments = ManagementFactory.getRuntimeMXBean().inputArguments
                        assertTrue("--enable-native-access=ALL-UNNAMED" in arguments, arguments.toString())
                    }
                }
                """);
        assertEquals(TaskOutcome.SUCCESS, library.run("test").task(":test").getOutcome());

        TestProject application = TestProject.in(dir.resolve("application"))
            .buildScript("plugins { id(\"gradlenew.kotlin-jvm-application\") }")
            .file("src/main/kotlin/gradlenew/testproject/Main.kt", """
                package gradlenew.testproject

                import java.lang.management.ManagementFactory

                fun main() = println("jvm arguments: " + ManagementFactory.getRuntimeMXBean().inputArguments)
                """);
        assertTrue(application.run("run").getOutput().contains("--enable-native-access=ALL-UNNAMED"));
    }
}
