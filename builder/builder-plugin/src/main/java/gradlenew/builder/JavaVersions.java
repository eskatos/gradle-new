package gradlenew.builder;

import org.gradle.api.JavaVersion;

final class JavaVersions {

    private JavaVersions() {
    }

    static int highestKnown() {
        int highest = 0;
        for (JavaVersion version : JavaVersion.values()) {
            if (version != JavaVersion.VERSION_HIGHER) {
                highest = Math.max(highest, Integer.parseInt(version.getMajorVersion()));
            }
        }
        return highest;
    }
}
