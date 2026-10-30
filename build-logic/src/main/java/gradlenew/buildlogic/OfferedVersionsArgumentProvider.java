package gradlenew.buildlogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class OfferedVersionsArgumentProvider implements CommandLineArgumentProvider {

    @Nested
    public abstract OfferedSlotArguments getStable();

    @Nested
    public abstract OfferedSlotArguments getRc();

    @Nested
    public abstract OfferedSlotArguments getPrevious();

    @Input
    @Optional
    public abstract Property<String> getStableDistributionSha256();

    @Input
    @Optional
    public abstract Property<String> getStableWrapperSha256();

    static OfferedVersionsArgumentProvider of(Project project, OfferedVersions offered, Catalog catalog, Provider<Boolean> enabled) {
        OfferedVersionsArgumentProvider arguments = project.getObjects().newInstance(OfferedVersionsArgumentProvider.class);
        Provider<String> none = project.getProviders().provider(() -> null);
        for (String slot : OfferedVersions.SLOTS) {
            OfferedSlotArguments slotArguments = arguments.slot(slot);
            Provider<String> version = enabled.flatMap(use -> use ? offered.version(slot) : none);
            Provider<Integer> java = version.flatMap(present -> DaemonJava.version(offered, slot, catalog));
            slotArguments.getVersion().set(version);
            slotArguments.getDaemonJava().set(java);
            slotArguments.getJavaHome().set(DaemonJava.home(project, java).map(home -> home.getAsFile().getAbsolutePath()));
        }
        return arguments;
    }

    private OfferedSlotArguments slot(String slot) {
        return Map.of("stable", getStable(), "rc", getRc(), "previous", getPrevious()).get(slot);
    }

    @Override
    public Iterable<String> asArguments() {
        List<String> arguments = new ArrayList<>();
        for (String slot : OfferedVersions.SLOTS) {
            OfferedSlotArguments slotArguments = slot(slot);
            if (slotArguments.getVersion().isPresent()) {
                arguments.add("-D" + OfferedVersions.PROPERTY_PREFIX + slot + "=" + slotArguments.getVersion().get());
            }
            if (slotArguments.getJavaHome().isPresent()) {
                arguments.add("-D" + OfferedVersions.PROPERTY_PREFIX + slot + ".java-home=" + slotArguments.getJavaHome().get());
            }
        }
        if (getStableDistributionSha256().isPresent() && getStableWrapperSha256().isPresent()) {
            arguments.add("-D" + OfferedVersions.STABLE_PROPERTY + ".distribution-sha256=" + getStableDistributionSha256().get());
            arguments.add("-D" + OfferedVersions.STABLE_PROPERTY + ".wrapper-sha256=" + getStableWrapperSha256().get());
        }
        return arguments;
    }
}
