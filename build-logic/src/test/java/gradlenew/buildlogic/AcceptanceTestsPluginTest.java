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

        BuildResult result = project.run("test", "-Pacceptance.target=http://localhost:1234", "-Pgradlenew.offered.stable=9.8.2");

        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
        assertEquals("http://localhost:1234|null", Files.readString(project.path("probe.txt")));
    }

    @Test
    void passesOfferedVersionsToTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.acceptance-tests\") }")
            .file("src/test/kotlin/ProbeTest.kt", """
                import kotlin.test.Test
                import java.io.File

                class ProbeTest {
                    @Test
                    fun recordsOfferedVersions() {
                        File("probe.txt").writeText(
                            System.getProperty("acceptance.offered.stable") + "|" + System.getProperty("acceptance.offered.rc")
                        )
                    }
                }
                """);

        project.run("test", "-Pgradlenew.offered.stable=9.8.2", "-Pgradlenew.offered.rc=");
        assertEquals("9.8.2|null", Files.readString(project.path("probe.txt")));

        project.run("test", "--rerun", "-Pgradlenew.offered.stable=9.8.2", "-Pgradlenew.offered.rc=9.9.0-rc-1");
        assertEquals("9.8.2|9.9.0-rc-1", Files.readString(project.path("probe.txt")));
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

        BuildResult result = project.run("test", "-Pgradlenew.offered.stable=9.8.2");

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
