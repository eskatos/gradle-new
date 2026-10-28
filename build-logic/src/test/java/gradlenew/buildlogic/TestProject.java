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
        project.file("settings.gradle.kts", """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
                versionCatalogs {
                    create("libs") {
                        from(files("%s"))
                    }
                }
            }
            rootProject.name = "test-project"
            """.formatted(catalogPath()));
        project.file("gradle.properties", """
            org.gradle.unsafe.isolated-projects=true
            org.gradle.jvmargs=-Xmx512m
            """);
        return project;
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
