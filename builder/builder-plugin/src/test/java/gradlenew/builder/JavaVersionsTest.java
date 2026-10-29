package gradlenew.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.gradle.api.JavaVersion;
import org.junit.jupiter.api.Test;

class JavaVersionsTest {

    @Test
    void highestKnownIsTheLastConstantBeforeVersionHigher() {
        JavaVersion[] versions = JavaVersion.values();
        JavaVersion lastKnown = versions[Arrays.asList(versions).indexOf(JavaVersion.VERSION_HIGHER) - 1];

        assertEquals(Integer.parseInt(lastKnown.getMajorVersion()), JavaVersions.highestKnown());
        assertTrue(JavaVersions.highestKnown() >= 25);
    }
}
