package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void assemblesSiteTreeAndAgentFilesFromSources(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.static-site\") }")
            .file("src/site/index.html", "<!doctype html><title>site</title>")
            .file("src/site/versions.json", "{\"stable\":\"9.8.2\"}")
            .file("src/agent/SKILL.md", "---\nname: test\n---\n");

        BuildResult result = project.run("assemble");

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleSite").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleAgent").getOutcome());
        assertTrue(Files.exists(project.path("build/site/index.html")));
        assertTrue(Files.exists(project.path("build/site/versions.json")));
        assertTrue(Files.exists(project.path("build/agent/SKILL.md")));
        assertFalse(Files.exists(project.path("build/site/SKILL.md")));
    }

    @Test
    void rendersThePreviewImageOnlyWhenAsked(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("""
                plugins { id("gradlenew.static-site") }

                tasks.named<JavaExec>("renderPreviewImage") {
                    classpath = files(sourceSets.test.map { it.output }) + configurations.testRuntimeClasspath.get()
                    mainClass = "PrintArgumentsKt"
                    dependsOn("testClasses")
                }
                """)
            .file("src/site/index.html", "<!doctype html><title>site</title>")
            .file("src/site/assets/logo/logo.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
            .file("src/preview/preview.html", "<!doctype html><title>preview</title>")
            .file("src/test/kotlin/PrintArguments.kt", """
                fun main(arguments: Array<String>) = println("playwright " + arguments.joinToString(" "))
                """);

        BuildResult planned = project.run("build", "--dry-run");
        assertFalse(planned.getOutput().contains(":renderPreviewImage"), planned.getOutput());

        BuildResult rendered = project.run("renderPreviewImage", "-x", "installPlaywrightBrowsers");
        Path preview = project.path("build/preview").toRealPath();
        assertTrue(rendered.getOutput().contains("playwright screenshot --browser chromium --viewport-size 1200,630 --wait-for-timeout 1000 "
            + preview.resolve("page/preview.html").toUri() + " " + preview.resolve("preview.png") + "\n"), rendered.getOutput());
        assertTrue(Files.exists(project.path("build/preview/page/assets/logo/logo.svg")));
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
