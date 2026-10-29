package gradlenew.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

final class PlaywrightBrowsers {

    static final String INSTALL_TASK = "installPlaywrightBrowsers";
    static final String SKIP_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
    static final String ENGINES_PROPERTY = "playwright.engines";
    static final String ENGINES = "webkit,firefox,chromium";

    private PlaywrightBrowsers() {
    }

    static void wire(Project project, Catalog catalog) {
        project.getDependencies().addProvider("testImplementation", catalog.library("playwright"));
        TaskProvider<JavaExec> install = project.getTasks().register(INSTALL_TASK, JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Installs WebKit, Firefox and Chromium's headless shell into Playwright's browser cache.");
            task.setClasspath(project.getConfigurations().getByName("testRuntimeClasspath"));
            task.getMainClass().set("com.microsoft.playwright.CLI");
            task.args("install", "--only-shell", "chromium", "firefox", "webkit");
            task.doNotTrackState("Playwright keeps its browsers in a cache outside the build");
        });
        String engines = project.getProviders().gradleProperty(ENGINES_PROPERTY).getOrElse(ENGINES);
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.dependsOn(install);
            test.environment(SKIP_DOWNLOAD, "1");
            test.systemProperty(ENGINES_PROPERTY, engines);
        });
    }
}
