package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceTestsPluginTest {

    @Test
    void passesTargetAndImageReferenceToTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.acceptance-tests\") }")
            .file("src/test/kotlin/ProbeTest.kt", """
                import kotlin.test.Test
                import java.io.File

                class ProbeTest {
                    @Test
                    fun recordsSystemProperties() {
                        File("probe.txt").writeText(
                            System.getProperty("acceptance.target") + "|" + System.getProperty("acceptance.imageRef")
                        )
                    }
                }
                """);

        BuildResult result = project.run("test", "-Pacceptance.target=http://localhost:1234");

        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
        assertEquals("http://localhost:1234|null", Files.readString(project.path("probe.txt")));
    }

    @Test
    void handsThePublisherClasspathBuilderSourcesAndCatalogToTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"tool\", \"acceptance\")\n")
            .file("builder/settings.gradle.kts", "rootProject.name = \"builder\"\n")
            .file("tool/build.gradle.kts", "plugins { id(\"gradlenew.kotlin-jvm-application\") }")
            .file("tool/src/main/kotlin/gradlenew/tool/Main.kt", """
                package gradlenew.tool

                fun main() = println("tool")
                """)
            .file("acceptance/build.gradle.kts", """
                plugins { id("gradlenew.acceptance-tests") }
                dependencies { publisher(project(":tool")) }
                """)
            .file("acceptance/src/test/kotlin/ProbeTest.kt", """
                import kotlin.test.Test
                import java.io.File

                class ProbeTest {
                    @Test
                    fun recordsPublisherInputs() {
                        File("probe.txt").writeText(
                            listOf("acceptance.publisherClasspath", "acceptance.builderSources", "acceptance.versionCatalog", "gradlenew.offered.stable")
                                .joinToString("\\n") { System.getProperty(it) ?: "null" }
                        )
                    }
                }
                """);

        BuildResult result = project.run(":acceptance:test", "-Pgradlenew.offered.stable=9.8.2");

        assertEquals(TaskOutcome.SUCCESS, result.task(":acceptance:test").getOutcome());
        List<String> recorded = Files.readAllLines(project.path("acceptance/probe.txt"));
        List<String> classpath = List.of(recorded.get(0).split(File.pathSeparator));
        assertTrue(classpath.stream().anyMatch(entry -> entry.endsWith("tool.jar")), classpath.toString());
        assertTrue(classpath.stream().anyMatch(entry -> new File(entry).getName().startsWith("kotlin-stdlib")), classpath.toString());
        assertEquals(dir.toRealPath().resolve("builder").toString(), recorded.get(1));
        assertEquals(dir.toRealPath().resolve("gradle/libs.versions.toml").toString(), recorded.get(2));
        assertEquals("9.8.2", recorded.get(3));
    }

    @Test
    void defaultsToImageTarget(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.acceptance-tests\") }")
            .file("src/test/kotlin/ProbeTest.kt", """
                import kotlin.test.Test
                import kotlin.test.assertEquals

                class ProbeTest {
                    @Test
                    fun targetDefaultsToImage() {
                        assertEquals("image", System.getProperty("acceptance.target"))
                    }
                }
                """);

        BuildResult result = project.run("test");

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
