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

class PublishSiteTest {

    @Test
    void publishesEachOfferedVersionWithItsOwnTaskAndWritesTheIndex(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=9.9.0-rc-1\n");

        BuildResult result = project.run(":my-tool:publishSite");

        assertEquals(TaskOutcome.SUCCESS, result.task(":my-tool:publishGradle-9.8.2").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":my-tool:publishGradle-9.9.0-rc-1").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":my-tool:publishVersionsIndex").getOutcome());
        String stable = Files.readString(project.path("my-tool/build/published/9.8.2/site/v/9.8.2/args.txt"));
        Path root = dir.toRealPath();
        assertTrue(stable.startsWith("publish --version 9.8.2 "), stable);
        assertTrue(stable.contains("--builder-dir " + root.resolve("builder") + " "), stable);
        assertTrue(stable.contains("--catalog " + root.resolve("catalog.toml") + " "), stable);
        assertTrue(stable.contains("--output " + root.resolve("my-tool/build/published/9.8.2/site") + " "), stable);
        assertTrue(stable.contains("--templates-output " + root.resolve("my-tool/build/published/9.8.2/templates") + " "), stable);
        assertTrue(stable.contains("--work-dir " + root.resolve("my-tool/build/publisher-work/9.8.2") + " "), stable);
        assertTrue(stable.contains("--report-output " + root.resolve("my-tool/build/published/9.8.2/report") + " "), stable);
        assertTrue(stable.endsWith("--template-budget-mb 64"), stable);
        assertTrue(Files.readString(project.path("my-tool/build/published/9.9.0-rc-1/site/v/9.9.0-rc-1/args.txt")).startsWith("publish --version 9.9.0-rc-1 "));
        assertEquals("index --stable 9.8.2 --rc 9.9.0-rc-1 --output " + root.resolve("my-tool/build/published/index"),
            Files.readString(project.path("my-tool/build/published/index/versions.json")));
    }

    @Test
    void takesTheTemplateBudgetFromAGradleProperty(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");

        project.run(":my-tool:publishSite", "-Pgradlenew.templates.budget-mb=5");

        assertTrue(Files.readString(project.path("my-tool/build/published/9.8.2/site/v/9.8.2/args.txt")).endsWith("--template-budget-mb 5"));
    }

    @Test
    void failsOnANonZeroExitAndNamesTheReport(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.9.9\nrc=\n");

        BuildResult result = project.runAndFail(":my-tool:publishSite");

        assertEquals(TaskOutcome.FAILED, result.task(":my-tool:publishGradle-9.9.9").getOutcome());
        assertTrue(result.getOutput().contains("The publisher exited with 1 for Gradle 9.9.9"), result.getOutput());
        assertTrue(result.getOutput().contains(dir.toRealPath().resolve("my-tool/build/published/9.9.9/report/verification-report.json").toString()), result.getOutput());
    }

    @Test
    void addingAnRcReusesThePublishedStableVersion(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");
        project.run(":my-tool:publishSite", "--build-cache");

        project.file("offered-gradle-versions.properties", "stable=9.8.2\nrc=9.9.0-rc-1\n");
        BuildResult withRc = project.run(":my-tool:publishSite", "--build-cache");
        assertEquals(TaskOutcome.UP_TO_DATE, withRc.task(":my-tool:publishGradle-9.8.2").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, withRc.task(":my-tool:publishGradle-9.9.0-rc-1").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, withRc.task(":my-tool:publishVersionsIndex").getOutcome());

        project.run(":my-tool:clean");
        BuildResult afterClean = project.run(":my-tool:publishSite", "--build-cache");
        assertEquals(TaskOutcome.FROM_CACHE, afterClean.task(":my-tool:publishGradle-9.8.2").getOutcome());
        assertEquals(TaskOutcome.FROM_CACHE, afterClean.task(":my-tool:publishGradle-9.9.0-rc-1").getOutcome());
        assertTrue(Files.exists(project.path("my-tool/build/published/9.8.2/report/verification-report.json")));
    }

    @Test
    void isUpToDateDespiteBuildOutputsOfTheBuilderAndRerunsWhenItsSourcesChange(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");
        String[] arguments = {":my-tool:publishSite", "--build-cache"};

        project.run(arguments);
        project.file("builder/build/libs/stale.jar", "changes between runs");
        project.file("builder/.gradle/file-hashes.bin", "changes between runs");
        project.file("builder/builder-plugin/build/libs/stale.jar", "changes between runs");
        assertEquals(TaskOutcome.UP_TO_DATE, project.run(arguments).task(":my-tool:publishGradle-9.8.2").getOutcome());

        Files.writeString(project.path("builder/settings.gradle.kts"), "rootProject.name = \"changed\"\n");
        assertEquals(TaskOutcome.SUCCESS, project.run(arguments).task(":my-tool:publishGradle-9.8.2").getOutcome());
    }

    @Test
    void exposesTheIndexAndEveryVersionAsTheSiteVariantAndTheTemplatesSeparately(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir)
            .file("offered-gradle-versions.properties", "stable=9.8.2\nrc=9.9.0-rc-1\n")
            .file("settings.gradle.kts", TestProject.settings() + "include(\"my-tool\", \"consumer\")\n")
            .file("consumer/build.gradle.kts", """
                plugins { base }
                fun collector(kind: String) = configurations.create(kind) {
                    isCanBeConsumed = false
                    attributes { attribute(Attribute.of("gradlenew.artifact", String::class.java), kind) }
                }
                val site = collector("site")
                val templates = collector("templates")
                dependencies {
                    site(project(":my-tool"))
                    templates(project(":my-tool"))
                }
                tasks.register<Sync>("collectSite") {
                    from(site)
                    into(layout.buildDirectory.dir("site"))
                }
                tasks.register<Sync>("collectTemplates") {
                    from(templates)
                    into(layout.buildDirectory.dir("templates"))
                }
                """);

        project.run(":consumer:collectSite", ":consumer:collectTemplates");

        assertTrue(Files.exists(project.path("consumer/build/site/versions.json")));
        assertTrue(Files.exists(project.path("consumer/build/site/v/9.8.2/args.txt")));
        assertTrue(Files.exists(project.path("consumer/build/site/v/9.9.0-rc-1/args.txt")));
        assertFalse(Files.exists(project.path("consumer/build/site/9.8.2")));
        assertTrue(Files.exists(project.path("consumer/build/templates/9.8.2/key.zip")));
        assertTrue(Files.exists(project.path("consumer/build/templates/9.9.0-rc-1/key.zip")));
        assertFalse(Files.exists(project.path("consumer/build/templates/versions.json")));
    }

    @Test
    void failsClearlyWhenNoStableVersionIsOffered(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir);

        BuildResult result = project.runAndFail(":my-tool:publishSite");

        assertTrue(result.getOutput().contains("No stable Gradle version is offered"), result.getOutput());
        assertTrue(result.getOutput().contains("offered-gradle-versions.properties"), result.getOutput());
    }

    @Test
    void proposeVersionsRunsTheProposeCommandOnThePinFileEveryTime(@TempDir Path dir) throws IOException {
        TestProject project = publisherProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\n");

        BuildResult first = project.run(":my-tool:proposeVersions", "-Pgradlenew.feeds-url=http://127.0.0.1:9/versions/");
        BuildResult second = project.run(":my-tool:proposeVersions");

        assertEquals(TaskOutcome.SUCCESS, first.task(":my-tool:proposeVersions").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, second.task(":my-tool:proposeVersions").getOutcome());
        String pin = dir.toRealPath().resolve("offered-gradle-versions.properties").toString();
        assertEquals("propose --pin " + pin + " --write", Files.readString(project.path("propose-args.txt")));
        assertTrue(first.getOutput().contains("propose --pin " + pin + " --write --feeds-url http://127.0.0.1:9/versions/"), first.getOutput());
    }

    private static TestProject publisherProject(Path dir) throws IOException {
        return TestProject.in(dir)
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
                import kotlin.system.exitProcess

                fun main(args: Array<String>) {
                    fun option(name: String) = args[args.indexOf("--" + name) + 1]
                    val recorded = args.joinToString(" ")
                    when (args[0]) {
                        "publish" -> {
                            val version = option("version")
                            File(option("report-output")).apply { mkdirs() }.resolve("verification-report.json").writeText("{}")
                            if (version == "9.9.9") exitProcess(1)
                            File(option("output")).resolve("v/" + version).apply { mkdirs() }.resolve("args.txt").writeText(recorded)
                            File(option("templates-output")).resolve(version).apply { mkdirs() }.resolve("key.zip").writeText("template")
                        }
                        "index" -> File(option("output")).apply { mkdirs() }.resolve("versions.json").writeText(recorded)
                        "propose" -> {
                            println(recorded)
                            File(option("pin")).parentFile.resolve("propose-args.txt").writeText(recorded)
                        }
                    }
                }
                """);
    }
}
