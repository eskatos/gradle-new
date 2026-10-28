package gradlenew.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;

final class JvmConventions {

    private JvmConventions() {
    }

    static void applyKotlinJvm(Project project, Catalog catalog) {
        project.getPluginManager().apply("org.jetbrains.kotlin.jvm");
        project.getExtensions().getByType(KotlinJvmProjectExtension.class).jvmToolchain(catalog.jdk());
        project.getDependencies().add("testImplementation", "org.jetbrains.kotlin:kotlin-test");
        project.getDependencies().addProvider("testRuntimeOnly", catalog.library("junit-platform-launcher"));
        OfferedVersions offered = OfferedVersions.of(project.getProviders());
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.getInputs().property(OfferedVersions.STABLE_PROPERTY, offered.stable()).optional(true);
            test.getInputs().property(OfferedVersions.RC_PROPERTY, offered.rc()).optional(true);
            test.doFirst(task -> {
                Test running = (Test) task;
                if (offered.stable().isPresent()) {
                    running.systemProperty(OfferedVersions.STABLE_PROPERTY, offered.stable().get());
                }
                if (offered.rc().isPresent()) {
                    running.systemProperty(OfferedVersions.RC_PROPERTY, offered.rc().get());
                }
            });
        });
    }
}
