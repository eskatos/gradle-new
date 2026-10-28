package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

public abstract class NativeImageServerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        project.getPluginManager().apply("application");
        JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
        application.getMainClass().set(KotlinJvmApplicationPlugin.mainClassFor(project.getName()));

        Configuration site = project.getConfigurations().create("site", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            ContainerImageAttributes.site(configuration.getAttributes());
        });
        TaskProvider<Sync> assembleSite = project.getTasks()
            .register("assembleSite", Sync.class, task -> {
                task.setDescription("Merges every site contributor into one site tree.");
                task.from(site);
                task.into(project.getLayout().getBuildDirectory().dir("site"));
            });
        Provider<String> siteDir = assembleSite.map(task -> task.getDestinationDir().getAbsolutePath());
        project.getTasks().named("run", JavaExec.class, run -> {
            run.getInputs().files(assembleSite);
            run.doFirst(task -> ((JavaExec) task).environment("SITE_DIR", siteDir.get()));
        });

        TaskProvider<Sync> assembleImageContext = project.getTasks()
            .register("assembleImageContext", Sync.class, task -> {
                task.setDescription("Assembles the container build context: application jars, Dockerfile and site tree.");
                task.into(project.getLayout().getBuildDirectory().dir("image/context"));
                task.from(project.getTasks().named("installDist", Sync.class).map(Sync::getDestinationDir),
                    copy -> copy.include("lib/**"));
                task.from(project.getLayout().getProjectDirectory().file("Dockerfile"));
                task.from(assembleSite, copy -> copy.into("site"));
            });

        TaskProvider<BuildContainerImageTask> buildImage = project.getTasks()
            .register("buildImage", BuildContainerImageTask.class, task -> {
                task.setGroup("build");
                task.setDescription("Builds the container image, compiling the native image inside the build.");
                task.getContextDirectory().fileProvider(assembleImageContext.map(Sync::getDestinationDir));
                task.getMainClass().set(application.getMainClass());
                task.getRepository().set("gradlenew/" + project.getName());
                task.getTool().set(ContainerTool.detect(project.getProviders()));
                task.getImageReferenceFile().set(project.getLayout().getBuildDirectory().file("image/image-ref.txt"));
            });

        project.getConfigurations().consumable("imageElements", configuration -> {
            ContainerImageAttributes.imageReference(configuration.getAttributes());
            configuration.getOutgoing().artifact(buildImage.flatMap(BuildContainerImageTask::getImageReferenceFile));
        });
    }
}
