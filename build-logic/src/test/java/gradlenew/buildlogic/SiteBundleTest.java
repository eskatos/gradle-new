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

class SiteBundleTest {

    private static String icon(String shapes) {
        return """
            <!-- @license lucide-static v0.383.0 - ISC -->
            <svg
              class="lucide"
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              %s
            </svg>
            """.formatted(shapes);
    }

    static TestProject siteProject(Path dir) throws IOException {
        return TestProject.in(dir)
            .buildScript("plugins { id(\"gradlenew.static-site\") }")
            .file("src/site/assets/icons/copy.svg", icon("<rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" />"))
            .file("src/site/assets/icons/check.svg", icon("<path d=\"M20 6 9 17l-5-5\" />"));
    }

    @Test
    void assemblesTheIconsIntoOneSpriteWithAViewPerIcon(@TempDir Path dir) throws IOException {
        TestProject project = siteProject(dir);

        BuildResult result = project.run("assembleIconSprite");

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleIconSprite").getOutcome());
        String sprite = Files.readString(project.path("build/bundle/assets/icons.svg"));
        assertEquals(1, sprite.split("@license lucide-static v0.383.0 - ISC", -1).length - 1, sprite);
        assertTrue(sprite.contains("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 48 24\">"), sprite);
        assertTrue(sprite.contains("<view id=\"check\" viewBox=\"0 0 24 24\"/>"), sprite);
        assertTrue(sprite.contains("<view id=\"copy\" viewBox=\"24 0 24 24\"/>"), sprite);
        assertTrue(sprite.contains("<svg x=\"24\" y=\"0\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" /></svg>"), sprite);
        assertTrue(sprite.contains("<path d=\"M20 6 9 17l-5-5\" />"), sprite);
        assertTrue(!sprite.contains("class=\"lucide\""), sprite);
    }
}
