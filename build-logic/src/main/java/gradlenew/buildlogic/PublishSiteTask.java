package gradlenew.buildlogic;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTask
public abstract class PublishSiteTask extends JavaExec {

    @Input
    public abstract Property<String> getStable();

    @Input
    @Optional
    public abstract Property<String> getRc();

    @Internal
    public abstract DirectoryProperty getBuilderSources();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBuilderSourceFiles();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getVersionCatalog();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Override
    public void exec() {
        List<String> arguments = new ArrayList<>(List.of(
            "--stable", getStable().get(),
            "--rc", getRc().getOrElse(""),
            "--builder-dir", getBuilderSources().get().getAsFile().getAbsolutePath(),
            "--catalog", getVersionCatalog().get().getAsFile().getAbsolutePath(),
            "--output", getOutputDirectory().get().getAsFile().getAbsolutePath(),
            "--work-dir", getWorkDirectory().get().getAsFile().getAbsolutePath()));
        setArgs(arguments);
        super.exec();
    }
}
