package gradlenew.builder.api;

import java.util.List;

public interface BuildType {

    String getId();

    String getComponentType();

    String getLanguage();

    Choice getDsls();

    Choice getModularization();

    List<? extends TestFrameworkChoice> getTestFrameworks();

    boolean isPackageSupported();

    boolean isJavaVersionSupported();

    boolean isProjectNameSupported();

    String getFurtherReading();
}
