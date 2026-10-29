package gradlenew.buildlogic;

import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension;

public abstract class KotlinCommonLibraryPlugin implements Plugin<Project> {

    private static final Set<String> KOTLIN_STANDARD_LIBRARY = Set.of(
        "org.jetbrains.kotlin:kotlin-stdlib",
        "org.jetbrains:annotations");

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        project.getPluginManager().apply("org.jetbrains.kotlin.multiplatform");
        KotlinMultiplatformExtension kotlin = project.getExtensions().getByType(KotlinMultiplatformExtension.class);
        kotlin.jvm();
        kotlin.jvmToolchain(catalog.jdk());
        kotlin.getCompilerOptions().getAllWarningsAsErrors().set(true);
        JvmConventions.enableNativeAccess(project);
        project.getDependencies().add("commonTestImplementation", "org.jetbrains.kotlin:kotlin-test");
        project.getDependencies().addProvider("jvmTestRuntimeOnly", catalog.library("junit-platform-launcher"));
        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);

        TaskProvider<DependencyFreeCheckTask> checkDependencyFree = project.getTasks()
            .register("checkDependencyFree", DependencyFreeCheckTask.class, task -> {
                task.setGroup("verification");
                task.setDescription("Fails if the module depends on anything beyond the Kotlin standard library.");
                task.getAllowedModules().set(KOTLIN_STANDARD_LIBRARY);
                task.getProjectPath().set(project.getPath());
                for (String configuration : Set.of("jvmCompileClasspath", "jvmRuntimeClasspath")) {
                    task.getRootComponents().add(project.getConfigurations().getByName(configuration)
                        .getIncoming().getResolutionResult().getRootComponent());
                }
            });
        project.getTasks().named("check", task -> task.dependsOn(checkDependencyFree));
    }
}
