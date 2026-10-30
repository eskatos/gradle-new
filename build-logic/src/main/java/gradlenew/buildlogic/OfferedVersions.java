package gradlenew.buildlogic;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

record OfferedVersions(Provider<String> stable, Provider<String> rc, Provider<String> previous, Provider<Properties> pinned, ProviderFactory providers) {

    static final String FILE = "offered-gradle-versions.properties";
    static final String PROPERTY_PREFIX = "gradlenew.offered.";
    static final String STABLE_PROPERTY = PROPERTY_PREFIX + "stable";
    static final String RC_PROPERTY = PROPERTY_PREFIX + "rc";
    static final String PREVIOUS_PROPERTY = PROPERTY_PREFIX + "previous";
    static final List<String> SLOTS = List.of("stable", "rc", "previous");

    static OfferedVersions of(Project project) {
        ProviderFactory providers = project.getProviders();
        RegularFile file = pinFile(project);
        Provider<Properties> pinned = providers.fileContents(file).getAsText().map(OfferedVersions::parse);
        return new OfferedVersions(slot(providers, pinned, "stable"), slot(providers, pinned, "rc"), slot(providers, pinned, "previous"), pinned, providers);
    }

    private static Provider<String> slot(ProviderFactory providers, Provider<Properties> pinned, String key) {
        return providers.gradleProperty(PROPERTY_PREFIX + key).orElse(pinned.map(properties -> properties.getProperty(key, ""))).filter(value -> !value.isBlank());
    }

    Provider<String> version(String slot) {
        return switch (slot) {
            case "stable" -> stable;
            case "rc" -> rc;
            case "previous" -> previous;
            default -> throw new IllegalArgumentException("Unknown slot " + slot);
        };
    }

    Provider<Integer> daemonJava(String slot) {
        return slot(providers, pinned, slot + ".daemon-java").map(Integer::valueOf);
    }

    Provider<String> distributionSha256(String version) {
        return pinnedChecksum(version, "distribution");
    }

    Provider<String> wrapperSha256(String version) {
        return pinnedChecksum(version, "wrapper");
    }

    private Provider<String> pinnedChecksum(String version, String kind) {
        return pinned.map(properties -> {
            for (String slot : SLOTS) {
                if (version.equals(properties.getProperty(slot, ""))) {
                    String checksum = properties.getProperty(slot + "." + kind + "-sha256", "");
                    return checksum.isEmpty() ? null : checksum;
                }
            }
            return null;
        });
    }

    static RegularFile pinFile(Project project) {
        return project.getLayout().getSettingsDirectory().file(FILE);
    }

    Map<String, String> slots() {
        Map<String, String> slots = new LinkedHashMap<>();
        for (String slot : SLOTS) {
            Provider<String> version = version(slot);
            if (version.isPresent()) {
                slots.put(slot, version.get());
            }
        }
        return slots;
    }

    List<String> versions() {
        return new ArrayList<>(slots().values());
    }

    private static Properties parse(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        properties.replaceAll((key, value) -> value.toString().trim());
        return properties;
    }
}
