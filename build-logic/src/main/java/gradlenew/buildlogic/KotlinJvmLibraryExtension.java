package gradlenew.buildlogic;

import org.gradle.api.provider.Property;

public abstract class KotlinJvmLibraryExtension {

    public abstract Property<Boolean> getTestsUseOfferedGradleVersions();
}
