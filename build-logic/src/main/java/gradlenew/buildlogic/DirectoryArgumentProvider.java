package gradlenew.buildlogic;

import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class DirectoryArgumentProvider implements CommandLineArgumentProvider {

    @Input
    public abstract Property<String> getSystemProperty();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDirectory();

    @Override
    public Iterable<String> asArguments() {
        return List.of("-D" + getSystemProperty().get() + "=" + getDirectory().get().getAsFile().getAbsolutePath());
    }
}
