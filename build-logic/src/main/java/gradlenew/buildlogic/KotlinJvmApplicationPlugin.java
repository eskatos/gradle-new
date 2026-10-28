package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;

public abstract class KotlinJvmApplicationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        JvmConventions.applyKotlinJvm(project, Catalog.of(project));
        project.getPluginManager().apply("application");
        project.getExtensions().getByType(JavaApplication.class).getMainClass().set(mainClassFor(project.getName()));
    }

    static String mainClassFor(String projectName) {
        return "gradlenew." + projectName.replace("-", "") + ".MainKt";
    }
}
