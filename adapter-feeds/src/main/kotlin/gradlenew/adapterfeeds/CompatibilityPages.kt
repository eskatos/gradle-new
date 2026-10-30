package gradlenew.adapterfeeds

import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CompatibilityPages(private val baseUrl: URI = DEFAULT_BASE_URL, timeout: Duration = 20.seconds) {

    private val source = TextSource(timeout)

    fun fetch(version: String): String {
        require(VERSION.matches(version)) { "Not a Gradle version: '$version'" }
        return source.get(baseUrl.resolve("$version/userguide/compatibility.html"), "The compatibility page", "text/html")
    }

    companion object {
        val DEFAULT_BASE_URL: URI = URI("https://docs.gradle.org/")
        private val VERSION = Regex("""\d+(\.\d+)+(-rc-\d+)?""")
    }
}
