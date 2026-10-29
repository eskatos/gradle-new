package gradlenew.buildlogic;

import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class PublisherInputsArgumentProvider implements CommandLineArgumentProvider {

    @Classpath
    public abstract ConfigurableFileCollection getPublisherClasspath();

    @Internal
    public abstract DirectoryProperty getBuilderSources();

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBuilderSourceFiles();

    @Internal
    public abstract RegularFileProperty getVersionCatalog();

    @Internal
    public abstract DirectoryProperty getDistributionCache();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getVersionCatalogFiles();

    @Override
    public Iterable<String> asArguments() {
        return List.of(
            "-Dacceptance.publisherClasspath=" + getPublisherClasspath().getAsPath(),
            "-Dacceptance.builderSources=" + getBuilderSources().get().getAsFile().getAbsolutePath(),
            "-Dacceptance.versionCatalog=" + getVersionCatalog().get().getAsFile().getAbsolutePath(),
            "-Dacceptance.distributionCache=" + getDistributionCache().get().getAsFile().getAbsolutePath());
    }
}
