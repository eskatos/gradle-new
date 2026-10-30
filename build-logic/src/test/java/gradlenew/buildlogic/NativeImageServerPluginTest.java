package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeImageServerPluginTest {

    @Test
    void registersImageTasksAndNoHostNativeCompilation(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.native-image-server\") }");

        BuildResult result = project.run("tasks", "--all", "-q");

        assertTrue(result.getOutput().contains("assembleImageContext"));
        assertTrue(result.getOutput().contains("buildImage"));
        assertFalse(result.getOutput().contains("nativeCompile"));
    }

    @Test
    void assemblesTheImageContextFromJarsDockerfileSiteTemplatesAndAgentFiles(@TempDir Path dir) throws IOException {
        TestProject project = serverWithSite(dir)
            .file("server/Dockerfile", "FROM scratch\nARG MAIN_CLASS\nCOPY lib lib\nCOPY site /site\n");

        project.run(":server:assembleImageContext", "-q");

        Path context = dir.resolve("server/build/image/context");
        assertTrue(Files.exists(context.resolve("Dockerfile")));
        assertTrue(Files.exists(context.resolve("lib/server.jar")));
        try (var jars = Files.list(context.resolve("lib"))) {
            assertTrue(jars.anyMatch(jar -> jar.getFileName().toString().startsWith("kotlin-stdlib-")));
        }
        assertTrue(Files.exists(context.resolve("site/versions.json")));
        assertTrue(Files.exists(context.resolve("templates/9.8.2/key.zip")));
        assertTrue(Files.exists(context.resolve("agent/SKILL.md")));
        assertFalse(Files.exists(context.resolve("site/9.8.2")));
        assertFalse(Files.exists(context.resolve("site/SKILL.md")));
        assertFalse(Files.exists(context.resolve("bin")));
    }

    @Test
    void runsOnTheJvmWithTheMergedSiteTreeTheTemplatesAndTheAgentFiles(@TempDir Path dir) throws IOException {
        TestProject project = serverWithSite(dir);

        BuildResult result = project.run(":server:run", "-q");

        assertTrue(result.getOutput().contains("site has {\"stable\":\"9.8.2\"} and extra"), result.getOutput());
        assertTrue(result.getOutput().contains("templates have template"), result.getOutput());
        assertTrue(result.getOutput().contains("agent has skill"), result.getOutput());
    }

    @Test
    void mergesEverySiteContributorIntoTheImageContext(@TempDir Path dir) throws IOException {
        TestProject project = serverWithSite(dir)
            .file("server/Dockerfile", "FROM scratch\n");

        project.run(":server:assembleImageContext", "-q");

        assertTrue(Files.exists(dir.resolve("server/build/image/context/site/versions.json")));
        assertTrue(Files.exists(dir.resolve("server/build/image/context/site/extra.txt")));
    }

    @Test
    void tagsTheImageWithTheOfferedVersionsAndTheCommit(@TempDir Path dir) throws IOException, InterruptedException {
        TestProject project = imageProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=9.9.0-rc-1\nprevious=8.14.6\n");
        git(dir, "init", "--quiet");
        git(dir, "add", ".");
        git(dir, "-c", "user.name=test", "-c", "user.email=test@example.org", "commit", "--quiet", "-m", "initial");
        String commit = git(dir, "rev-parse", "--short=12", "HEAD");

        project.runWithEnvironment(fakeTool(dir), ":server:buildImage");
        assertEquals("gradlenew/server:9.8.2--8.14.6--9.9.0-rc-1--" + commit, imageReference(dir));
        assertTrue(Files.readString(dir.resolve("build/tool-args.txt")).contains("--tag gradlenew/server:9.8.2--8.14.6--9.9.0-rc-1--" + commit + " "));

        project.file("ui/src/site/versions.json", "{\"stable\":\"9.8.2\",\"changed\":true}");
        project.runWithEnvironment(fakeTool(dir), ":server:buildImage");
        assertTrue(imageReference(dir).matches("gradlenew/server:9\\.8\\.2--8\\.14\\.6--9\\.9\\.0-rc-1--" + commit + "-dirty-[0-9a-f]{16}"), imageReference(dir));
    }

    @Test
    void withoutACommitTheTagCarriesTheContentHash(@TempDir Path dir) throws IOException {
        TestProject project = imageProject(dir).file("offered-gradle-versions.properties", "stable=9.8.2\nrc=\nprevious=8.14.6\n");

        project.runWithEnvironment(fakeTool(dir), ":server:buildImage");

        assertTrue(imageReference(dir).matches("gradlenew/server:9\\.8\\.2--8\\.14\\.6--dirty-[0-9a-f]{16}"), imageReference(dir));
    }

    private static TestProject imageProject(Path dir) throws IOException {
        TestProject project = serverWithSite(dir)
            .file("server/Dockerfile", "FROM scratch\n")
            .file(".gitignore", "build/\n.gradle/\n.kotlin/\n.build-cache/\n")
            .file("fake-tool.sh", "#!/bin/sh\nmkdir -p \"$(dirname \"$0\")/build\"\necho \"$@\" > \"$(dirname \"$0\")/build/tool-args.txt\"\n");
        Files.setPosixFilePermissions(dir.resolve("fake-tool.sh"), PosixFilePermissions.fromString("rwxr-xr-x"));
        return project;
    }

    private static Map<String, String> fakeTool(Path dir) {
        return Map.of("GRADLENEW_CONTAINER_TOOL", dir.resolve("fake-tool.sh").toString());
    }

    private static String imageReference(Path dir) throws IOException {
        return Files.readString(dir.resolve("server/build/image/image-ref.txt")).trim();
    }

    private static String git(Path dir, String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", dir.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        assertTrue(process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0, output);
        return output;
    }

    private static TestProject serverWithSite(Path dir) throws IOException {
        return TestProject.in(dir)
            .file("settings.gradle.kts", TestProject.settings() + "include(\"ui\", \"extra\", \"tpl\", \"server\")\n")
            .file("ui/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }")
            .file("ui/src/site/versions.json", "{\"stable\":\"9.8.2\"}")
            .file("ui/src/agent/SKILL.md", "skill")
            .file("extra/build.gradle.kts", "plugins { id(\"gradlenew.static-site\") }")
            .file("extra/src/site/extra.txt", "extra")
            .file("tpl/build.gradle.kts", """
                plugins { base }
                configurations.consumable("templatesElements") {
                    attributes { attribute(Attribute.of("gradlenew.artifact", String::class.java), "templates") }
                    outgoing.artifact(layout.projectDirectory.dir("content"))
                }
                """)
            .file("tpl/content/9.8.2/key.zip", "template")
            .file("server/build.gradle.kts", """
                plugins { id("gradlenew.native-image-server") }
                dependencies {
                    site(project(":ui"))
                    site(project(":extra"))
                    templates(project(":tpl"))
                    agent(project(":ui"))
                }
                """)
            .file("server/src/main/kotlin/gradlenew/server/Main.kt", """
                package gradlenew.server

                import java.io.File

                fun main() {
                    val site = File(System.getenv("SITE_DIR"))
                    println("site has " + site.resolve("versions.json").readText() + " and " + site.resolve("extra.txt").readText())
                    println("templates have " + File(System.getenv("TEMPLATES_DIR")).resolve("9.8.2/key.zip").readText())
                    println("agent has " + File(System.getenv("AGENT_DIR")).resolve("SKILL.md").readText())
                }
                """);
    }
}
