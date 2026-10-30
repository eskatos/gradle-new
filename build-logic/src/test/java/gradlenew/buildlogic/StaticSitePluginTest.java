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

    @Test
    void testsReceiveTheAssembledSiteTheAgentFilesAndInstalledBrowsers(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.static-site\") }")
            .file("src/site/index.html", "<!doctype html><title>site</title>")
            .file("src/agent/SKILL.md", "---\nname: test\n---\n")
            .file("src/test/kotlin/SiteTest.kt", """
                import java.io.File
                import kotlin.test.Test
                import kotlin.test.assertEquals
                import kotlin.test.assertTrue

                class SiteTest {
                    @Test
                    fun seesTheSite() {
                        assertTrue(File(System.getProperty("ui.siteDir"), "index.html").isFile)
                        assertTrue(File(System.getProperty("ui.agentDir"), "SKILL.md").isFile)
                        assertEquals("1", System.getenv("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"))
                        assertEquals("webkit,firefox,chromium", System.getProperty("playwright.engines"))
                        Class.forName("com.microsoft.playwright.Playwright")
                    }
                }
                """);

        BuildResult planned = project.run("test", "--dry-run");
        assertTrue(planned.getOutput().contains(":installPlaywrightBrowsers SKIPPED"), planned.getOutput());

        BuildResult result = project.run("test", "-x", "installPlaywrightBrowsers");
        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
    }
}
