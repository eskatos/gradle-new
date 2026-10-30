package gradlenew.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

final class DaemonJava {

    private DaemonJava() {
    }

    static Provider<Integer> version(OfferedVersions offered, String slot, Catalog catalog) {
        return offered.daemonJava(slot).orElse(catalog.jdk());
    }

    static Provider<Directory> home(Project project, Provider<Integer> version) {
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        return version.flatMap(java -> toolchains.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(java))))
            .map(launcher -> launcher.getMetadata().getInstallationPath());
    }
}
