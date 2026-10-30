package gradlenew.buildlogic;

import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

@CacheableTask
public abstract class PublishVersionsIndexTask extends JavaExec {

    @Input
    @Optional
    public abstract Property<String> getStable();

    @Input
    @Optional
    public abstract Property<String> getRc();

    @Input
    @Optional
    public abstract Property<String> getPrevious();

    @OutputDirectory
    public abstract DirectoryProperty getIndexDirectory();

    @Override
    public void exec() {
        if (!getStable().isPresent()) {
            throw new GradleException("No stable Gradle version is offered: create " + OfferedVersions.FILE
                + " at the repository root with stable=<version>, or pass -P" + OfferedVersions.STABLE_PROPERTY + "=<version>");
        }
        if (!getPrevious().isPresent()) {
            throw new GradleException("No previous major Gradle version is offered: add previous=<version> to " + OfferedVersions.FILE
                + " at the repository root, or pass -P" + OfferedVersions.PREVIOUS_PROPERTY + "=<version>");
        }
        setArgs(List.of(
            "index",
            "--stable", getStable().get(),
            "--rc", getRc().getOrElse(""),
            "--previous", getPrevious().get(),
            "--output", getIndexDirectory().get().getAsFile().getAbsolutePath()));
        super.exec();
    }
}
