package gradlenew.buildlogic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

public abstract class BuildContainerImageTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getContextDirectory();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getRepository();

    @Input
    public abstract Property<String> getTool();

    @OutputFile
    public abstract RegularFileProperty getImageReferenceFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void build() throws IOException {
        Path context = getContextDirectory().get().getAsFile().toPath();
        String reference = getRepository().get() + ":" + contentHash(context, getMainClass().get());
        getExecOperations().exec(exec -> exec.commandLine(
            getTool().get(), "build", "--quiet",
            "--build-arg", "MAIN_CLASS=" + getMainClass().get(),
            "--tag", reference,
            "--file", context.resolve("Dockerfile").toString(),
            context.toString()));
        Files.writeString(getImageReferenceFile().get().getAsFile().toPath(), reference + "\n");
    }

    private static String contentHash(Path root, String mainClass) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(mainClass.getBytes(StandardCharsets.UTF_8));
            files.filter(Files::isRegularFile).sorted().forEach(file -> {
                try {
                    digest.update(root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                    digest.update(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
