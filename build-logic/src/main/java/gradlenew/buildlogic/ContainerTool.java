package gradlenew.buildlogic;

import java.io.File;
import java.util.List;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

final class ContainerTool {

    private static final List<String> CANDIDATES = List.of("podman", "docker");

    private ContainerTool() {
    }

    static Provider<String> detect(ProviderFactory providers) {
        return providers.environmentVariable("GRADLENEW_CONTAINER_TOOL")
            .orElse(providers.environmentVariable("PATH").map(ContainerTool::firstOnPath));
    }

    private static String firstOnPath(String path) {
        for (String candidate : CANDIDATES) {
            for (String dir : path.split(File.pathSeparator)) {
                File executable = new File(dir, candidate);
                if (executable.canExecute()) {
                    return executable.getAbsolutePath();
                }
            }
        }
        throw new IllegalStateException("No container tool found on PATH, looked for " + CANDIDATES);
    }
}
