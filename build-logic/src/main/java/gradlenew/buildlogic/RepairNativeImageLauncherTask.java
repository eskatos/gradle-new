package gradlenew.buildlogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

public abstract class RepairNativeImageLauncherTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getInstallation();

    @TaskAction
    public void repair() throws IOException {
        Path installation = getInstallation().get().getAsFile().toPath();
        Path launcher = installation.resolve("bin/native-image");
        Path target = Path.of("../lib/svm/bin/native-image");
        if (!Files.exists(installation.resolve("lib/svm/bin/native-image")) || Files.isSymbolicLink(launcher)) {
            return;
        }
        if (Files.exists(launcher) && Files.size(launcher) > 0) {
            return;
        }
        Files.deleteIfExists(launcher);
        Files.createSymbolicLink(launcher, target);
        getLogger().lifecycle("Repaired {} as a symbolic link to {} (lost during toolchain provisioning)", launcher, target);
    }
}
