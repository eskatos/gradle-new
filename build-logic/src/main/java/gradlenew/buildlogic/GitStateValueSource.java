package gradlenew.buildlogic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

public abstract class GitStateValueSource implements ValueSource<String, GitStateValueSource.Parameters> {

    static final String DIRTY = "+dirty";

    public interface Parameters extends ValueSourceParameters {
        DirectoryProperty getRepository();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public String obtain() {
        String commit = git("rev-parse", "--short=12", "HEAD");
        if (commit == null || commit.isBlank()) {
            return "";
        }
        String status = git("status", "--porcelain");
        return commit + (status == null || !status.isBlank() ? DIRTY : "");
    }

    private String git(String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>(List.of("git", "-C", getParameters().getRepository().get().getAsFile().getAbsolutePath()));
        command.addAll(List.of(arguments));
        try {
            ExecResult result = getExecOperations().exec(exec -> {
                exec.commandLine(command);
                exec.setStandardOutput(output);
                exec.setErrorOutput(new ByteArrayOutputStream());
                exec.setIgnoreExitValue(true);
            });
            return result.getExitValue() == 0 ? output.toString(StandardCharsets.UTF_8).trim() : null;
        } catch (RuntimeException gitUnavailable) {
            return null;
        }
    }
}
