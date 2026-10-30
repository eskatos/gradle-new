package gradlenew.domain.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestOriginTest {

    @Test
    fun aHostNameIsServedOverHttps() {
        assertEquals("https://gradle.example", RequestOrigin.of("gradle.example"))
        assertEquals("https://gradle.example:8443", RequestOrigin.of("gradle.example:8443"))
        assertEquals("https://gradle.example", RequestOrigin.of("Gradle.Example"))
        assertEquals("https://10.0.0.7", RequestOrigin.of("10.0.0.7"))
        assertEquals("https://[2001:db8::1]:8080", RequestOrigin.of("[2001:db8::1]:8080"))
    }

    @Test
    fun localhostAndLoopbackAddressesAreServedOverHttp() {
        assertEquals("http://localhost", RequestOrigin.of("localhost"))
        assertEquals("http://localhost:8080", RequestOrigin.of("localhost:8080"))
        assertEquals("http://127.0.0.1:41234", RequestOrigin.of("127.0.0.1:41234"))
        assertEquals("http://127.1.2.3", RequestOrigin.of("127.1.2.3"))
        assertEquals("http://[::1]:8080", RequestOrigin.of("[::1]:8080"))
        assertEquals("https://localhost.example", RequestOrigin.of("localhost.example"))
        assertEquals("https://127.0.0.1.example", RequestOrigin.of("127.0.0.1.example"))
    }

    @Test
    fun anythingElseIsRejected() {
        val invalid = listOf(
            null, "", " ", "not a host", "gradle.example/path", "gradle.example\r\nX: y", "gradle.example?x", "user@gradle.example",
            "gradle.example:", "gradle.example:0", "gradle.example:65536", "gradle.example:80:80", "gradle.example:http",
            "-gradle.example", "gradle-.example", "gradle..example", "gradle.example.", "gradle_new.example",
            "a".repeat(64) + ".example", ("a".repeat(63) + ".").repeat(4) + "example",
            "[::1", "::1", "[gradle.example]", "<script>", "{{origin}}",
        )
        for (host in invalid) assertEquals(null, RequestOrigin.of(host), "'$host'")
    }
}
