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

class OfferedVersionsTest {

    @Test
    void readsThePinFileAtTheRepositoryRoot(@TempDir Path dir) throws IOException {
        TestProject project = probe(dir).file("offered-gradle-versions.properties", "stable=9.9.0\nrc=9.10.0-rc-1\n");

        project.run(":acceptance:test");

        assertEquals("9.9.0|9.10.0-rc-1", Files.readString(project.path("acceptance/probe.txt")));
    }

    @Test
    void anEmptyRcInThePinFileMeansNone(@TempDir Path dir) throws IOException {
        TestProject project = probe(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");

        project.run(":acceptance:test");

        assertEquals("9.8.2|null", Files.readString(project.path("acceptance/probe.txt")));
    }

    @Test
    void gradlePropertiesOverrideThePinFile(@TempDir Path dir) throws IOException {
        TestProject project = probe(dir).file("offered-gradle-versions.properties", "stable=9.9.0\nrc=9.10.0-rc-1\n");

        project.run(":acceptance:test", "-Pgradlenew.offered.stable=9.8.2", "-Pgradlenew.offered.rc=");
        assertEquals("9.8.2|null", Files.readString(project.path("acceptance/probe.txt")));

        project.run(":acceptance:test", "-Pgradlenew.offered.rc=9.8.0-rc-3");
        assertEquals("9.9.0|9.8.0-rc-3", Files.readString(project.path("acceptance/probe.txt")));
    }

    @Test
    void aMissingPinFileOffersNothingWithoutBreakingTheBuild(@TempDir Path dir) throws IOException {
        TestProject project = probe(dir);

        BuildResult result = project.run(":acceptance:test");

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        assertEquals("null|null", Files.readString(project.path("acceptance/probe.txt")));
    }

    @Test
    void aChangedPinFileIsPickedUpByTheNextBuild(@TempDir Path dir) throws IOException {
        TestProject project = probe(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");
        project.run(":acceptance:test");

        project.file("offered-gradle-versions.properties", "stable=9.9.0\nrc=\n");
        BuildResult result = project.run(":acceptance:test");

        assertEquals(TaskOutcome.SUCCESS, result.task(":acceptance:test").getOutcome());
        assertEquals("9.9.0|null", Files.readString(project.path("acceptance/probe.txt")));
    }

    private static TestProject probe(Path dir) throws IOException {
        return TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"acceptance\")\n")
            .file("acceptance/build.gradle.kts", "plugins { id(\"gradlenew.acceptance-tests\") }")
            .file("acceptance/src/test/kotlin/ProbeTest.kt", """
                import kotlin.test.Test
                import java.io.File

                class ProbeTest {
                    @Test
                    fun recordsOfferedVersions() {
                        File("probe.txt").writeText(
                            System.getProperty("gradlenew.offered.stable") + "|" + System.getProperty("gradlenew.offered.rc")
                        )
                    }
                }
                """);
    }
}
