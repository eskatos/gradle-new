package gradlenew.buildlogic;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTask
public abstract class PublishVersionTask extends JavaExec {

    @Input
    public abstract Property<String> getGradleVersion();

    @Internal
    public abstract DirectoryProperty getBuilderSources();

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBuilderSourceFiles();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getVersionCatalog();

    @Input
    public abstract Property<Integer> getTemplateBudgetMegabytes();

    @Input
    @Optional
    public abstract Property<String> getDistributionSha256();

    @Input
    @Optional
    public abstract Property<String> getWrapperSha256();

    @Internal
    public abstract DirectoryProperty getDistributionCache();

    @OutputDirectory
    public abstract DirectoryProperty getSiteDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getTemplatesDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getReportDirectory();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Override
    public void exec() {
        List<String> arguments = new ArrayList<>(List.of(
            "publish",
            "--version", getGradleVersion().get(),
            "--builder-dir", getBuilderSources().get().getAsFile().getAbsolutePath(),
            "--catalog", getVersionCatalog().get().getAsFile().getAbsolutePath(),
            "--output", getSiteDirectory().get().getAsFile().getAbsolutePath(),
            "--templates-output", getTemplatesDirectory().get().getAsFile().getAbsolutePath(),
            "--work-dir", getWorkDirectory().get().getAsFile().getAbsolutePath(),
            "--report-output", getReportDirectory().get().getAsFile().getAbsolutePath(),
            "--distribution-cache", getDistributionCache().get().getAsFile().getAbsolutePath()));
        if (getDistributionSha256().isPresent() && getWrapperSha256().isPresent()) {
            arguments.addAll(List.of("--distribution-sha256", getDistributionSha256().get(), "--wrapper-sha256", getWrapperSha256().get()));
        }
        arguments.addAll(List.of("--template-budget-mb", String.valueOf(getTemplateBudgetMegabytes().get())));
        setArgs(arguments);
        setIgnoreExitValue(true);
        super.exec();
        int exitValue = getExecutionResult().get().getExitValue();
        if (exitValue != 0) {
            throw new GradleException("The publisher exited with " + exitValue + " for Gradle " + getGradleVersion().get()
                + "; see the verification report at "
                + getReportDirectory().file("verification-report.json").get().getAsFile().getAbsolutePath());
        }
    }
}
