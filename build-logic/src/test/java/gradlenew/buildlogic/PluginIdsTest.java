package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PluginIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "gradlenew.kotlin-common-library",
        "gradlenew.kotlin-jvm-library",
        "gradlenew.kotlin-jvm-application",
        "gradlenew.native-image-server",
        "gradlenew.static-site",
        "gradlenew.acceptance-tests",
    })
    void appliesUnderIsolatedProjects(String pluginId, @TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"%s\") }".formatted(pluginId));

        BuildResult result = project.run("help", "--stacktrace");

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
