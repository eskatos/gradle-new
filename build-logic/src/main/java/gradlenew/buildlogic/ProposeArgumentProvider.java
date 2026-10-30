package gradlenew.buildlogic;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class ProposeArgumentProvider implements CommandLineArgumentProvider {

    @Input
    public abstract Property<String> getPinFile();

    @Input
    @Optional
    public abstract Property<String> getFeedsUrl();

    @Input
    @Optional
    public abstract Property<String> getProposalsDirectory();

    @Override
    public Iterable<String> asArguments() {
        List<String> arguments = new ArrayList<>(List.of("propose", "--pin", getPinFile().get(), "--write"));
        if (getFeedsUrl().isPresent()) {
            arguments.addAll(List.of("--feeds-url", getFeedsUrl().get()));
        }
        if (getProposalsDirectory().isPresent()) {
            arguments.addAll(List.of("--proposals-dir", getProposalsDirectory().get()));
        }
        return arguments;
    }
}
