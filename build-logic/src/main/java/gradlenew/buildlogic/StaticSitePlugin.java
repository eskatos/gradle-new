package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

public abstract class StaticSitePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);

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
    }
}
