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
        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);
    }
}
