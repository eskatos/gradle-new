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

class PublishSiteTest {

    @Test
    void runsTheApplicationWithPinnedVersionsAndDefaultLocationsIntoTheSiteTree(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir);

        BuildResult result = project.run(":my-tool:publishSite", "-Pgradlenew.offered.stable=9.8.2", "-Pgradlenew.offered.rc=9.9.0-rc-1");

        assertEquals(TaskOutcome.SUCCESS, result.task(":my-tool:publishSite").getOutcome());
        String recorded = Files.readString(project.path("my-tool/build/site/args.txt"));
        assertTrue(recorded.contains("--stable 9.8.2 --rc 9.9.0-rc-1 "), recorded);
        assertTrue(recorded.contains("--builder-dir " + dir.toRealPath().resolve("builder") + " "), recorded);
        assertTrue(recorded.contains("--catalog " + dir.toRealPath().resolve("catalog.toml") + " "), recorded);
        assertTrue(recorded.contains("--output " + dir.toRealPath().resolve("my-tool/build/site") + " "), recorded);
        assertTrue(recorded.contains("--work-dir " + dir.toRealPath().resolve("my-tool/build/publisher-work")), recorded);
    }

    @Test
    void isUpToDateAndCacheableOnUnchangedInputs(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir);
        String[] arguments = {":my-tool:publishSite", "-Pgradlenew.offered.stable=9.8.2", "--build-cache"};

        project.run(arguments);
        project.file("builder/build/libs/stale.jar", "changes between runs");
        project.file("builder/.gradle/file-hashes.bin", "changes between runs");
        assertEquals(TaskOutcome.UP_TO_DATE, project.run(arguments).task(":my-tool:publishSite").getOutcome());

        project.run(":my-tool:clean");
        assertEquals(TaskOutcome.FROM_CACHE, project.run(arguments).task(":my-tool:publishSite").getOutcome());

        Files.writeString(project.path("builder/settings.gradle.kts"), "rootProject.name = \"changed\"\n");
        assertEquals(TaskOutcome.SUCCESS, project.run(arguments).task(":my-tool:publishSite").getOutcome());
    }

    @Test
    void exposesTheSiteTreeAsASiteVariant(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"my-tool\", \"consumer\")\n")
            .file("consumer/build.gradle.kts", """
                plugins { base }
                val site by configurations.creating {
                    isCanBeConsumed = false
                    attributes { attribute(Attribute.of("gradlenew.artifact", String::class.java), "site") }
                }
                dependencies { site(project(":my-tool")) }
                tasks.register<Sync>("collectSite") {
                    from(site)
                    into(layout.buildDirectory.dir("collected"))
                }
                """);

        project.run(":consumer:collectSite", "-Pgradlenew.offered.stable=9.8.2");

        assertTrue(Files.exists(project.path("consumer/build/collected/args.txt")));
    }

    private static TestProject publisherProject(Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"my-tool\")\n")
            .file("catalog.toml", "[versions]\n")
            .file("builder/settings.gradle.kts", "rootProject.name = \"builder\"\n")
            .file("my-tool/build.gradle.kts", """
                plugins { id("gradlenew.kotlin-jvm-application") }
                publishedSite { versionCatalog = layout.settingsDirectory.file("catalog.toml") }
                """)
            .file("my-tool/src/main/kotlin/gradlenew/mytool/Main.kt", """
                package gradlenew.mytool

                import java.io.File

                fun main(args: Array<String>) {
                    val output = File(args[args.indexOf("--output") + 1])
                    output.mkdirs()
                    output.resolve("args.txt").writeText(args.joinToString(" "))
                }
                """);
        return project;
    }
}
