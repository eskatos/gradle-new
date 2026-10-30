package gradlenew.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaywrightBrowsersTest {

    @Test
    void installsTheSystemLibrariesOnlyWhenAsked(@TempDir Path dir) throws IOException {
        TestProject project = TestProject.in(dir)
            .buildScript("""
                plugins { id("gradlenew.acceptance-tests") }

                tasks.named<JavaExec>("installPlaywrightBrowsers") {
                    classpath = files(sourceSets.test.map { it.output }) + configurations.testRuntimeClasspath.get()
                    mainClass = "PrintArgumentsKt"
                    dependsOn("testClasses")
                }
                """)
            .file("src/test/kotlin/PrintArguments.kt", """
                fun main(arguments: Array<String>) = println("playwright " + arguments.joinToString(" "))
                """);

        BuildResult plain = project.run("installPlaywrightBrowsers");
        assertTrue(plain.getOutput().contains("playwright install --only-shell chromium firefox webkit\n"), plain.getOutput());

        BuildResult withDeps = project.run("installPlaywrightBrowsers", "-Pplaywright.withDeps=true");
        assertTrue(withDeps.getOutput().contains("playwright install --with-deps --only-shell chromium firefox webkit\n"), withDeps.getOutput());
    }
}
