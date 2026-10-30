package gradlenew.buildlogic;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

public abstract class OfferedSlotArguments {

    @Input
    @Optional
    public abstract Property<String> getVersion();

    @Input
    @Optional
    public abstract Property<Integer> getDaemonJava();

    @Internal
    public abstract Property<String> getJavaHome();
}
