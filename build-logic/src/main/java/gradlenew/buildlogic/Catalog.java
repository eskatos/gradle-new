package gradlenew.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.provider.Provider;

final class Catalog {

    private final VersionCatalog libs;

    private Catalog(VersionCatalog libs) {
        this.libs = libs;
    }

    static Catalog of(Project project) {
        return new Catalog(project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs"));
    }

    int jdk() {
        return Integer.parseInt(libs.findVersion("jdk").orElseThrow().getRequiredVersion());
    }

    Provider<MinimalExternalModuleDependency> library(String alias) {
        return libs.findLibrary(alias).orElseThrow();
    }
}
