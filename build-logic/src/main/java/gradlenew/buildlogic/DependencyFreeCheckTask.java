package gradlenew.buildlogic;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class DependencyFreeCheckTask extends DefaultTask {

    @Input
    public abstract ListProperty<ResolvedComponentResult> getRootComponents();

    @Input
    public abstract SetProperty<String> getAllowedModules();

    @Input
    public abstract Property<String> getProjectPath();

    @TaskAction
    public void check() {
        Set<String> offenders = new TreeSet<>();
        Set<ResolvedComponentResult> visited = new HashSet<>();
        for (ResolvedComponentResult root : getRootComponents().get()) {
            visit(root, visited, offenders);
        }
        if (!offenders.isEmpty()) {
            throw new GradleException(
                "Project '" + getProjectPath().get() + "' must not depend on external modules, found:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    private void visit(ResolvedComponentResult component, Set<ResolvedComponentResult> visited, Set<String> offenders) {
        if (!visited.add(component)) {
            return;
        }
        if (component.getId() instanceof ModuleComponentIdentifier module) {
            String coordinates = module.getGroup() + ":" + module.getModule();
            if (!getAllowedModules().get().contains(coordinates)) {
                offenders.add(coordinates + ":" + module.getVersion());
            }
        }
        for (var dependency : component.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                visit(resolved.getSelected(), visited, offenders);
            }
        }
    }
}
