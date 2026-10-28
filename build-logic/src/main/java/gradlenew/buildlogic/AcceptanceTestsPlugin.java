package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;

public abstract class AcceptanceTestsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Catalog catalog = Catalog.of(project);
        JvmConventions.applyKotlinJvm(project, catalog);
        project.getDependencies().addProvider("testImplementation", catalog.library("testcontainers"));
        project.getDependencies().addProvider("testRuntimeOnly", catalog.library("slf4j-simple"));

        Configuration image = project.getConfigurations().create("image", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            ContainerImageAttributes.imageReference(configuration.getAttributes());
        });

        Configuration site = project.getConfigurations().create("site", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            ContainerImageAttributes.site(configuration.getAttributes());
        });

        Provider<String> target = project.getProviders().gradleProperty("acceptance.target").orElse("image");
        Provider<String> dockerHost = project.getProviders().environmentVariable("DOCKER_HOST")
            .orElse(project.getProviders().environmentVariable("XDG_RUNTIME_DIR")
                .map(dir -> dir + "/podman/podman.sock")
                .filter(socket -> project.file(socket).exists())
                .map(socket -> "unix://" + socket));

        project.getTasks().named("test", Test.class, test -> {
            test.systemProperty("acceptance.target", target.get());
            ImageReferenceArgumentProvider imageReference = project.getObjects().newInstance(ImageReferenceArgumentProvider.class);
            imageReference.getImageReferenceFiles().setFrom(image);
            test.getJvmArgumentProviders().add(imageReference);
            SiteDirectoryArgumentProvider siteDirectory = project.getObjects().newInstance(SiteDirectoryArgumentProvider.class);
            siteDirectory.getSiteDirectories().setFrom(site);
            test.getJvmArgumentProviders().add(siteDirectory);
            if (dockerHost.isPresent()) {
                test.environment("DOCKER_HOST", dockerHost.get());
                test.environment("TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED", "true");
            }
        });
    }
}
