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

class StaticSitePluginTest {

    @Test
    void assemblesSiteTreeFromSources(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.static-site\") }")
            .file("src/site/index.html", "<!doctype html><title>site</title>")
            .file("src/site/versions.json", "{\"stable\":\"9.8.2\"}");

        BuildResult result = project.run("assemble");

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleSite").getOutcome());
        assertTrue(Files.exists(project.path("build/site/index.html")));
        assertTrue(Files.exists(project.path("build/site/versions.json")));
    }
}
