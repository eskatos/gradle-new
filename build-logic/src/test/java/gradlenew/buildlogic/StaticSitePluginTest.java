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
    void exposesSiteTreeToAcceptanceTests(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"ui\", \"acceptance\")\n")
            .file("ui/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }")
            .file("ui/src/site/versions.json", "{\"stable\":\"9.8.2\"}")
            .file("acceptance/build.gradle.kts", """
                plugins { id("gradlenew.acceptance-tests") }
                dependencies { site(project(":ui")) }
                """)
            .file("acceptance/src/test/kotlin/SiteTest.kt", """
                import java.io.File
                import kotlin.test.Test
                import kotlin.test.assertEquals

                class SiteTest {
                    @Test
                    fun siteDirectoryHoldsTheAssembledTree() {
                        val siteDir = File(System.getProperty("acceptance.siteDir"))
                        assertEquals("{\\"stable\\":\\"9.8.2\\"}", File(siteDir, "versions.json").readText())
                    }
                }
                """);

        BuildResult result = project.run(":acceptance:test", "-Pacceptance.target=http://unused");

        assertEquals(TaskOutcome.SUCCESS, result.task(":ui:assembleSite").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":acceptance:test").getOutcome());
    }
}
