package gradlenew.buildlogic;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class OfferedVersionsArgumentProvider implements CommandLineArgumentProvider {

    @Input
    @Optional
    public abstract Property<String> getStable();

    @Input
    @Optional
    public abstract Property<String> getRc();

    @Input
    @Optional
    public abstract Property<String> getStableDistributionSha256();

    @Input
    @Optional
    public abstract Property<String> getStableWrapperSha256();

    @Override
    public Iterable<String> asArguments() {
        List<String> arguments = new ArrayList<>();
        if (getStable().isPresent()) {
            arguments.add("-D" + OfferedVersions.STABLE_PROPERTY + "=" + getStable().get());
        }
        if (getRc().isPresent()) {
            arguments.add("-D" + OfferedVersions.RC_PROPERTY + "=" + getRc().get());
        }
        if (getStableDistributionSha256().isPresent() && getStableWrapperSha256().isPresent()) {
            arguments.add("-D" + OfferedVersions.STABLE_PROPERTY + ".distribution-sha256=" + getStableDistributionSha256().get());
            arguments.add("-D" + OfferedVersions.STABLE_PROPERTY + ".wrapper-sha256=" + getStableWrapperSha256().get());
        }
        return arguments;
    }
}
