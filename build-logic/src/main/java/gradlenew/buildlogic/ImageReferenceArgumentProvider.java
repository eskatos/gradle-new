package gradlenew.buildlogic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class ImageReferenceArgumentProvider implements CommandLineArgumentProvider {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getImageReferenceFiles();

    @Override
    public Iterable<String> asArguments() {
        return getImageReferenceFiles().getFiles().stream()
            .map(file -> {
                try {
                    return "-Dacceptance.imageRef=" + Files.readString(file.toPath()).trim();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .toList();
    }
}
