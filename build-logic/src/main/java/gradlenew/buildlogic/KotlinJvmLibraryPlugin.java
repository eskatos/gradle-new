package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;

public abstract class KotlinJvmLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        JvmConventions.applyKotlinJvm(project, Catalog.of(project));
        KotlinJvmLibraryExtension library = project.getExtensions().create("jvmLibrary", KotlinJvmLibraryExtension.class);
        library.getTestsUseOfferedGradleVersions().convention(false);
        OfferedVersions offered = OfferedVersions.of(project);
        Provider<String> none = project.getProviders().provider(() -> null);
        project.getTasks().withType(Test.class).configureEach(test -> {
            OfferedVersionsArgumentProvider versions = project.getObjects().newInstance(OfferedVersionsArgumentProvider.class);
            versions.getStable().set(library.getTestsUseOfferedGradleVersions().flatMap(use -> use ? offered.stable() : none));
            versions.getRc().set(library.getTestsUseOfferedGradleVersions().flatMap(use -> use ? offered.rc() : none));
            test.getJvmArgumentProviders().add(versions);
        });
    }
}
