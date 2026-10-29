package gradlenew.buildlogic;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

record OfferedVersions(Provider<String> stable, Provider<String> rc) {

    static final String FILE = "offered-gradle-versions.properties";
    static final String STABLE_PROPERTY = "gradlenew.offered.stable";
    static final String RC_PROPERTY = "gradlenew.offered.rc";

    static OfferedVersions of(Project project) {
        ProviderFactory providers = project.getProviders();
        RegularFile file = pinFile(project);
        Provider<Properties> pinned = providers.fileContents(file).getAsText().map(OfferedVersions::parse);
        return new OfferedVersions(
            providers.gradleProperty(STABLE_PROPERTY).orElse(pinned.map(properties -> properties.getProperty("stable", ""))).filter(value -> !value.isBlank()),
            providers.gradleProperty(RC_PROPERTY).orElse(pinned.map(properties -> properties.getProperty("rc", ""))).filter(value -> !value.isBlank()));
    }

    static RegularFile pinFile(Project project) {
        return project.getLayout().getSettingsDirectory().file(FILE);
    }

    List<String> versions() {
        List<String> versions = new ArrayList<>();
        if (stable.isPresent()) {
            versions.add(stable.get());
        }
        if (rc.isPresent()) {
            versions.add(rc.get());
        }
        return versions;
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
