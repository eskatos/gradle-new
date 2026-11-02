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

    static TestProject styledSiteProject(Path dir) throws IOException {
        return siteProject(dir)
            .file("src/site/index.html", """
                <!doctype html>
                <link rel="icon" href="assets/logo/icon.svg" type="image/svg+xml">
                <link rel="stylesheet" href="assets/tokens/fonts.css">
                <link rel="stylesheet" href="app/styles.css">
                <link rel="modulepreload" href="app/model.js">
                <script type="module" src="app/main.js"></script>
                """)
            .file("src/site/assets/tokens/fonts.css", "@font-face { font-family: 'Lato'; src: url(\"../fonts/lato.woff2\") format(\"woff2\"); }\n")
            .file("src/site/app/styles.css", ".icon-copy { --icon: url(\"../assets/icons/copy.svg\"); }\n.icon-check { --icon: url('/assets/icons/check.svg'); }\n.page { background: url(data:image/gif;base64,R0lGOD==); }\n")
            .file("src/site/assets/fonts/lato.woff2", "font")
            .file("src/site/assets/logo/icon.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>");
    }

    @Test
    void concatenatesTheLinkedStylesheetsInOrderWithTheirUrlsRewrittenForTheBundle(@TempDir Path dir) throws IOException {
        TestProject project = styledSiteProject(dir);

        BuildResult result = project.run("assembleStylesheet");

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleStylesheet").getOutcome());
        assertEquals("""
            @font-face { font-family: 'Lato'; src: url("fonts/lato.woff2") format("woff2"); }

            .icon-copy { --icon: url("icons.svg#copy"); }
            .icon-check { --icon: url("icons.svg#check"); }
            .page { background: url(data:image/gif;base64,R0lGOD==); }
            """, Files.readString(project.path("build/bundle/assets/styles.css")));
    }

    @Test
    void inlinesTheSvgImagesOfThePagesWithTheirAccessibleNameAndNoStyleElement(@TempDir Path dir) throws IOException {
        TestProject project = styledSiteProject(dir)
            .file("src/site/assets/logo/brand.svg", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- a logo -->
                <svg id="a" data-name="Layer 1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 50">
                  <defs>
                    <style>
                      .b {
                        fill: #2ca7be;
                      }

                      .c {
                        fill: #fff;
                      }
                    </style>
                    <linearGradient id="g"><stop offset="0"/></linearGradient>
                  </defs>
                  <rect class="c" x="0" y="0" width="16" height="16"/>
                  <path class="b" d="M0 0h10"/>
                  <path class="st0" fill="url(#g)" d="M1 1h10"/>
                </svg>
                """)
            .file("src/site/index.html", """
                <!doctype html>
                <a id="home" href="https://example.com/"><img class="site-logo" src="assets/logo/brand.svg" alt="Brand" height="20"></a>
                """)
            .file("src/site/maintenance.html", """
                <!doctype html>
                <a id="home" href="https://example.com/"><img class="site-logo" src="/assets/logo/brand.svg" alt="Brand" height="20"></a>
                """);

        BuildResult result = project.run("assembleSite");

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleSite").getOutcome());
        for (String page : new String[] {"index.html", "maintenance.html"}) {
            String html = Files.readString(project.path("build/site/" + page));
            assertTrue(!html.contains("<img"), html);
            assertTrue(html.contains("<svg class=\"site-logo\" role=\"img\" aria-label=\"Brand\" width=\"80\" height=\"20\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 50\" id=\"brand-a\">"), html);
            assertTrue(html.contains("<rect x=\"0\" y=\"0\" width=\"16\" height=\"16\" fill=\"#fff\"/>"), html);
            assertTrue(html.contains("<path d=\"M0 0h10\" fill=\"#2ca7be\"/>"), html);
            assertTrue(html.contains("<linearGradient id=\"brand-g\">"), html);
            assertTrue(html.contains("fill=\"url(#brand-g)\""), html);
            assertTrue(!html.contains("<style") && !html.contains("class=\"b\"") && !html.contains("class=\"st0\"") && !html.contains("<?xml") && !html.contains("data-name"), html);
        }
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
