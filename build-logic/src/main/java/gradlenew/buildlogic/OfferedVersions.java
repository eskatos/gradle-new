package gradlenew.buildlogic;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

record OfferedVersions(Provider<String> stable, Provider<String> rc) {

    static final String STABLE_PROPERTY = "gradlenew.offered.stable";
    static final String RC_PROPERTY = "gradlenew.offered.rc";

    static OfferedVersions of(ProviderFactory providers) {
        return new OfferedVersions(
            providers.gradleProperty(STABLE_PROPERTY),
            providers.gradleProperty(RC_PROPERTY).filter(value -> !value.isBlank()));
    }
}
