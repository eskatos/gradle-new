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
    static final String AGENT_DIR_PROPERTY = "ui.agentDir";

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
        TaskProvider<Sync> assembleAgent = project.getTasks().register("assembleAgent", Sync.class, task -> {
            task.setGroup("build");
            task.setDescription("Assembles the agent files the server renders, kept out of the site tree.");
            task.from(project.getLayout().getProjectDirectory().dir("src/agent"));
            task.into(project.getLayout().getBuildDirectory().dir("agent"));
        });
        project.getTasks().named("assemble", task -> task.dependsOn(assembleSite, assembleAgent));

        project.getConfigurations().consumable("siteElements", configuration -> {
            ContainerImageAttributes.site(configuration.getAttributes());
            configuration.getOutgoing().artifact(assembleSite.map(Sync::getDestinationDir));
        });
        project.getConfigurations().consumable("agentElements", configuration -> {
            ContainerImageAttributes.agent(configuration.getAttributes());
            configuration.getOutgoing().artifact(assembleAgent.map(Sync::getDestinationDir));
        });

        project.getTasks().named("test", Test.class, test -> {
            DirectoryArgumentProvider site = project.getObjects().newInstance(DirectoryArgumentProvider.class);
            site.getSystemProperty().set(SITE_DIR_PROPERTY);
            site.getDirectory().set(project.getLayout().dir(assembleSite.map(Sync::getDestinationDir)));
            test.getJvmArgumentProviders().add(site);
            DirectoryArgumentProvider agent = project.getObjects().newInstance(DirectoryArgumentProvider.class);
            agent.getSystemProperty().set(AGENT_DIR_PROPERTY);
            agent.getDirectory().set(project.getLayout().getProjectDirectory().dir("src/agent"));
            test.getJvmArgumentProviders().add(agent);
        });
    }
}
