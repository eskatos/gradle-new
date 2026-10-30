package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

public abstract class KotlinJvmLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        KotlinJvmLibraryExtension library = project.getExtensions().create("jvmLibrary", KotlinJvmLibraryExtension.class);
        library.getTestsUseOfferedGradleVersions().convention(false);
        OfferedVersions offered = OfferedVersions.of(project);
        project.getTasks().withType(Test.class).configureEach(test ->
            test.getJvmArgumentProviders().add(OfferedVersionsArgumentProvider.of(project, offered, catalog, library.getTestsUseOfferedGradleVersions())));
    }
}
