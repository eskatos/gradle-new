package gradlenew.buildlogic;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

public abstract class StaticSitePlugin implements Plugin<Project> {

    static final String SITE_DIR_PROPERTY = "ui.siteDir";
    static final String AGENT_DIR_PROPERTY = "ui.agentDir";

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        project.getDependencies().addProvider("testImplementation", catalog.library("junit-jupiter"));
        PlaywrightBrowsers.wire(project, catalog);

        Directory siteSources = project.getLayout().getProjectDirectory().dir("src/site");
        Provider<Directory> bundleDir = project.getLayout().getBuildDirectory().dir("bundle");
        TaskProvider<IconSpriteTask> assembleIconSprite = project.getTasks().register("assembleIconSprite", IconSpriteTask.class, task -> {
            task.setDescription("Assembles the site's icons into one SVG sprite with a view per icon.");
            task.getIcons().from(siteSources.dir("assets/icons").getAsFileTree().matching(pattern -> pattern.include("*.svg")));
            task.getSprite().set(bundleDir.map(dir -> dir.file("assets/icons.svg")));
        });
        TaskProvider<StylesheetBundleTask> assembleStylesheet = project.getTasks().register("assembleStylesheet", StylesheetBundleTask.class, task -> {
            task.setDescription("Concatenates the stylesheets index.html links into one, with their url()s rewritten for its place.");
            task.getSiteSources().set(siteSources);
            task.getStylesheet().set(bundleDir.map(dir -> dir.file("assets/styles.css")));
        });
        Provider<Directory> siteDir = project.getLayout().getBuildDirectory().dir("site");
        TaskProvider<SiteAssemblyTask> assembleSite = project.getTasks().register("assembleSite", SiteAssemblyTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Assembles the static site tree, with the pages' SVG images inlined.");
            task.getSiteSources().set(siteSources);
            task.getSprite().set(assembleIconSprite.flatMap(IconSpriteTask::getSprite));
            task.getStylesheet().set(assembleStylesheet.flatMap(StylesheetBundleTask::getStylesheet));
            task.getDestination().set(siteDir);
        });
        TaskProvider<Sync> assembleAgent = project.getTasks().register("assembleAgent", Sync.class, task -> {
            task.setGroup("build");
            task.setDescription("Assembles the agent files the server renders, kept out of the site tree.");
            task.from(project.getLayout().getProjectDirectory().dir("src/agent"));
            task.into(project.getLayout().getBuildDirectory().dir("agent"));
        });
        project.getTasks().named("assemble", task -> task.dependsOn(assembleSite, assembleAgent));

        Provider<Directory> previewDir = project.getLayout().getBuildDirectory().dir("preview");
        TaskProvider<Sync> assemblePreviewPage = project.getTasks().register("assemblePreviewPage", Sync.class, task -> {
            task.setDescription("Assembles the link preview's source page with the site's assets.");
            task.from(project.getLayout().getProjectDirectory().dir("src/preview"));
            task.from(project.getLayout().getProjectDirectory().dir("src/site/assets"), spec -> spec.into("assets"));
            task.into(previewDir.map(dir -> dir.dir("page")));
        });
        project.getTasks().register("renderPreviewImage", JavaExec.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Renders src/preview/preview.html to the 1200 by 630 link preview image build/preview/preview.png, to review and commit by hand.");
            task.dependsOn(assemblePreviewPage, PlaywrightBrowsers.INSTALL_TASK);
            task.setClasspath(project.getConfigurations().getByName("testRuntimeClasspath"));
            task.getMainClass().set("com.microsoft.playwright.CLI");
            task.environment(PlaywrightBrowsers.SKIP_DOWNLOAD, "1");
            task.getArgumentProviders().add(() -> List.of(
                "screenshot", "--browser", "chromium", "--viewport-size", "1200,630", "--wait-for-timeout", "1000",
                previewDir.get().file("page/preview.html").getAsFile().toPath().toUri().toString(),
                previewDir.get().file("preview.png").getAsFile().getPath()));
            task.doNotTrackState("Renders on demand, for a person to review");
        });

        project.getConfigurations().consumable("siteElements", configuration -> {
            ContainerImageAttributes.site(configuration.getAttributes());
            configuration.getOutgoing().artifact(assembleSite.flatMap(SiteAssemblyTask::getDestination));
        });
        project.getConfigurations().consumable("agentElements", configuration -> {
            ContainerImageAttributes.agent(configuration.getAttributes());
            configuration.getOutgoing().artifact(assembleAgent.map(Sync::getDestinationDir));
        });

        project.getTasks().named("test", Test.class, test -> {
            DirectoryArgumentProvider site = project.getObjects().newInstance(DirectoryArgumentProvider.class);
            site.getSystemProperty().set(SITE_DIR_PROPERTY);
            site.getDirectory().set(assembleSite.flatMap(SiteAssemblyTask::getDestination));
            test.getJvmArgumentProviders().add(site);
            DirectoryArgumentProvider agent = project.getObjects().newInstance(DirectoryArgumentProvider.class);
            agent.getSystemProperty().set(AGENT_DIR_PROPERTY);
            agent.getDirectory().set(project.getLayout().getProjectDirectory().dir("src/agent"));
            test.getJvmArgumentProviders().add(agent);
        });
    }
}
