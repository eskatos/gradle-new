package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

public abstract class StaticSitePlugin implements Plugin<Project> {

    static final String SITE_DIR_PROPERTY = "ui.siteDir";

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        project.getDependencies().addProvider("testImplementation", catalog.library("junit-jupiter"));
        PlaywrightBrowsers.wire(project, catalog);

        Provider<Directory> siteDir = project.getLayout().getBuildDirectory().dir("site");
        TaskProvider<Sync> assembleSite = project.getTasks().register("assembleSite", Sync.class, task -> {
            task.setGroup("build");
            task.setDescription("Assembles the static site tree.");
            task.from(project.getLayout().getProjectDirectory().dir("src/site"));
            task.into(siteDir);
        });
        project.getTasks().named("assemble", task -> task.dependsOn(assembleSite));

        project.getConfigurations().consumable("siteElements", configuration -> {
            ContainerImageAttributes.site(configuration.getAttributes());
            configuration.getOutgoing().artifact(assembleSite.map(Sync::getDestinationDir));
        });

        project.getTasks().named("test", Test.class, test -> {
            DirectoryArgumentProvider site = project.getObjects().newInstance(DirectoryArgumentProvider.class);
            site.getSystemProperty().set(SITE_DIR_PROPERTY);
            site.getDirectory().set(project.getLayout().dir(assembleSite.map(Sync::getDestinationDir)));
            test.getJvmArgumentProviders().add(site);
        });
    }
}
