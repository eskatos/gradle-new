package gradlenew.builder.api;

import java.util.List;

public interface InitModel {

    String getGradleVersion();

    List<? extends ComponentType> getComponentTypes();

    List<? extends NamedElement> getLanguages();

    List<? extends Dsl> getDsls();

    List<? extends NamedElement> getModularizationOptions();

    List<? extends NamedElement> getTestFrameworks();

    List<? extends BuildType> getBuildTypes();

    JavaVersionParameter getJavaVersion();

    TextParameter getPackageName();

    TextParameter getProjectName();

    List<? extends InitOption> getOptions();
}
