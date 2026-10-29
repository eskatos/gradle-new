package gradlenew.builder;

import gradlenew.builder.api.InitModel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.buildinit.plugins.internal.BuildGenerator;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.tasks.InitBuild;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

final class InitModelBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(InitModel.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        ProjectLayoutSetupRegistry registry = ((ProjectInternal) project).getServices().get(ProjectLayoutSetupRegistry.class);
        List<BuildGenerator> generators = new ArrayList<>();
        for (ComponentType componentType : registry.getComponentTypes()) {
            generators.addAll(registry.getGeneratorsFor(componentType));
        }
        InitModelConverter converter = new InitModelConverter(
            project.getGradle().getGradleVersion(),
            constant("MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API"),
            constant("DEFAULT_JAVA_VERSION"),
            JavaVersions.highestKnown(),
            constant("SOURCE_PACKAGE_DEFAULT"),
            InitOptions.read(InitBuild.class));
        return converter.convert(generators);
    }

    @SuppressWarnings("unchecked")
    private static <T> T constant(String name) {
        try {
            Field field = InitBuild.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Gradle's InitBuild no longer declares " + name, e);
        }
    }
}
