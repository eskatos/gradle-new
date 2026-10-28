package gradlenew.buildlogic;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class SiteDirectoryArgumentProvider implements CommandLineArgumentProvider {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSiteDirectories();

    @Override
    public Iterable<String> asArguments() {
        return getSiteDirectories().getFiles().stream()
            .map(dir -> "-Dacceptance.siteDir=" + dir.getAbsolutePath())
            .toList();
    }
}
