package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KotlinCommonLibraryPluginTest {

    private static final String GREETING = """
        package probe

        fun greeting(name: String) = "Hello, $name"
        """;

    private static final String GREETING_TEST = """
        package probe

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class GreetingTest {
            @Test
            fun greets() = assertEquals("Hello, Gradle", greeting("Gradle"))
        }
        """;

    @Test
    void buildsCommonCodeAndRunsCommonTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-common-library\") }")
            .file("src/commonMain/kotlin/probe/Greeting.kt", GREETING)
            .file("src/commonTest/kotlin/probe/GreetingTest.kt", GREETING_TEST);

        BuildResult result = project.run("build");

        assertEquals(TaskOutcome.SUCCESS, result.task(":jvmTest").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkDependencyFree").getOutcome());
    }

    @Test
    void failsOnExternalDependency(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("""
                plugins { id("gradlenew.kotlin-common-library") }
                dependencies { commonMainImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0") }
                """)
            .file("src/commonMain/kotlin/probe/Greeting.kt", GREETING);

        BuildResult result = project.runAndFail("checkDependencyFree");

        assertEquals(TaskOutcome.FAILED, result.task(":checkDependencyFree").getOutcome());
        assertTrue(result.getOutput().contains("org.jetbrains.kotlinx:kotlinx-serialization-json"));
    }

    @Test
    void allowsProjectDependencies(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"a\", \"b\")\n")
            .file("a/build.gradle.kts", "plugins { id(\"gradlenew.kotlin-common-library\") }")
            .file("a/src/commonMain/kotlin/probe/Greeting.kt", GREETING)
            .file("b/build.gradle.kts", """
                plugins { id("gradlenew.kotlin-common-library") }
                dependencies { commonMainApi(project(":a")) }
                """)
            .file("b/src/commonMain/kotlin/probe/Shout.kt", "package probe\n\nfun shout(name: String) = greeting(name).uppercase()\n");

        BuildResult result = project.run("build");

        assertEquals(TaskOutcome.SUCCESS, result.task(":b:checkDependencyFree").getOutcome());
    }

    @Test
    void failsOnACompilerWarning(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-common-library\") }")
            .file("src/commonMain/kotlin/probe/Greeting.kt", """
                package probe

                @Deprecated("Use greeting")
                fun hello(name: String) = "Hello, $name"

                fun greeting(name: String) = hello(name)
                """);

        BuildResult result = project.runAndFail("compileKotlinJvm");

        assertTrue(result.getOutput().contains("is deprecated. Use greeting"), result.getOutput());
    }

    @Test
    void jvmTestsHaveNativeAccess(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.kotlin-common-library\") }")
            .file("src/commonMain/kotlin/probe/Greeting.kt", GREETING)
            .file("src/jvmTest/kotlin/probe/NativeAccessTest.kt", """
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

        assertEquals(TaskOutcome.SUCCESS, project.run("jvmTest").task(":jvmTest").getOutcome());
    }
}
