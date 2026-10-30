package gradlenew.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

final class PlaywrightBrowsers {

    static final String INSTALL_TASK = "installPlaywrightBrowsers";
    static final String SKIP_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
    static final String ENGINES_PROPERTY = "playwright.engines";
    static final String ENGINES = "webkit,firefox,chromium";
    static final String WITH_DEPS_PROPERTY = "playwright.withDeps";
    static final String SERVICE = "browserTests";

    private PlaywrightBrowsers() {
    }

    static void wire(Project project, Catalog catalog) {
        project.getDependencies().addProvider("testImplementation", catalog.library("playwright"));
        boolean withDeps = project.getProviders().gradleProperty(WITH_DEPS_PROPERTY).map(Boolean::parseBoolean).getOrElse(false);
        Provider<BrowserTestsService> oneAtATime = project.getGradle().getSharedServices()
            .registerIfAbsent(SERVICE, BrowserTestsService.class, spec -> spec.getMaxParallelUsages().set(1));
        TaskProvider<JavaExec> install = project.getTasks().register(INSTALL_TASK, JavaExec.class, task -> {
            task.usesService(oneAtATime);
            task.setGroup("verification");
            task.setDescription("Installs WebKit, Firefox and Chromium's headless shell into Playwright's browser cache, and with -P" + WITH_DEPS_PROPERTY + "=true the system libraries they need, through apt.");
            task.setClasspath(project.getConfigurations().getByName("testRuntimeClasspath"));
            task.getMainClass().set("com.microsoft.playwright.CLI");
            task.args("install");
            if (withDeps) {
                task.args("--with-deps");
            }
            task.args("--only-shell", "chromium", "firefox", "webkit");
            task.doNotTrackState("Playwright keeps its browsers in a cache outside the build");
        });
        String engines = project.getProviders().gradleProperty(ENGINES_PROPERTY).getOrElse(ENGINES);
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.usesService(oneAtATime);
            test.dependsOn(install);
            test.environment(SKIP_DOWNLOAD, "1");
            test.systemProperty(ENGINES_PROPERTY, engines);
        });
    }
}
