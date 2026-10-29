package gradlenew.buildlogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

final class TestProject {

    private final Path dir;

    TestProject(Path dir) {
        this.dir = dir;
    }

    static TestProject in(Path dir) throws IOException {
        TestProject project = new TestProject(dir);
        project.file("settings.gradle.kts", settings());
        project.file("gradle.properties", """
            org.gradle.isolated-projects=true
            org.gradle.jvmargs=-Xmx512m
            """);
        return project;
    }

    static String settings() {
        return """
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
            }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
                versionCatalogs {
                    create("libs") {
                        from(files("%s"))
                    }
                }
            }
            rootProject.name = "test-project"
            buildCache {
                local {
                    directory = File(rootDir, ".build-cache")
                }
            }
            """.formatted(catalogPath());
    }

    private static String catalogPath() {
        return Path.of("..", "gradle", "libs.versions.toml").toAbsolutePath().normalize().toString();
    }

    TestProject file(String relativePath, String content) throws IOException {
        Path target = dir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return this;
    }

    TestProject buildScript(String content) throws IOException {
        return file("build.gradle.kts", content);
    }

    BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    BuildResult runWithEnvironment(java.util.Map<String, String> environment, String... arguments) {
        java.util.Map<String, String> merged = new java.util.HashMap<>(System.getenv());
        merged.putAll(environment);
        return runner(arguments).withEnvironment(merged).build();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput();
    }

    Path path(String relativePath) {
        return dir.resolve(relativePath);
    }
}
