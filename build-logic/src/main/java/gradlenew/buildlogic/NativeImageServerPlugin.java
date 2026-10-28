package gradlenew.buildlogic;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;

public abstract class NativeImageServerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        project.getPluginManager().apply("application");
        project.getExtensions().getByType(JavaApplication.class).getMainClass()
            .set(KotlinJvmApplicationPlugin.mainClassFor(project.getName()));

        Configuration site = project.getConfigurations().create("site", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            ContainerImageAttributes.site(configuration.getAttributes());
        });
        Provider<String> siteDir = site.getElements()
            .map(elements -> elements.iterator().next().getAsFile().getAbsolutePath());
        project.getTasks().named("run", JavaExec.class, run -> {
            run.getInputs().files(site);
            run.doFirst(task -> ((JavaExec) task).environment("SITE_DIR", siteDir.get()));
        });

        project.getPluginManager().apply("org.graalvm.buildtools.native");
        GraalVMExtension graalvm = project.getExtensions().getByType(GraalVMExtension.class);
        Provider<JavaLauncher> launcher = project.getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(spec -> {
                spec.getLanguageVersion().set(JavaLanguageVersion.of(catalog.jdk()));
                spec.getVendor().set(JvmVendorSpec.GRAAL_VM);
                spec.getNativeImageCapable().set(true);
            });
        graalvm.getBinaries().named("main", binary -> {
            binary.getImageName().set("server");
            binary.getJavaLauncher().set(launcher);
            binary.getBuildArgs().addAll(project.getProviders().environmentVariable("NATIVE_IMAGE_ARGS")
                .map(args -> List.of(args.trim().split("\\s+"))).orElse(List.of()));
        });

        TaskProvider<RepairNativeImageLauncherTask> repairLauncher = project.getTasks()
            .register("repairNativeImageLauncher", RepairNativeImageLauncherTask.class, task -> {
                task.setDescription("Restores the native-image launcher symlink lost by Gradle's toolchain provisioning.");
                task.getInstallation().fileProvider(launcher.map(l -> l.getMetadata().getInstallationPath().getAsFile()));
                task.getOutputs().upToDateWhen(t -> false);
            });
        project.getTasks().named("nativeCompile", task -> task.dependsOn(repairLauncher));

        TaskProvider<BuildContainerImageTask> buildImage = project.getTasks()
            .register("buildImage", BuildContainerImageTask.class, task -> {
                task.setGroup("build");
                task.setDescription("Builds the container image from the native binary and the site tree.");
                task.getBinary().set(project.getTasks().named("nativeCompile", BuildNativeImageTask.class)
                    .flatMap(BuildNativeImageTask::getOutputFile));
                task.getSite().fileProvider(site.getElements().map(elements -> elements.iterator().next().getAsFile()));
                task.getDockerfile().set(project.getLayout().getProjectDirectory().file("Dockerfile"));
                task.getRepository().set("gradlenew/" + project.getName());
                task.getTool().set(ContainerTool.detect(project.getProviders()));
                task.getContextDirectory().set(project.getLayout().getBuildDirectory().dir("image/context"));
                task.getImageReferenceFile().set(project.getLayout().getBuildDirectory().file("image/image-ref.txt"));
            });

        project.getConfigurations().consumable("imageElements", configuration -> {
            ContainerImageAttributes.imageReference(configuration.getAttributes());
            configuration.getOutgoing().artifact(buildImage.flatMap(BuildContainerImageTask::getImageReferenceFile));
        });
    }
}
