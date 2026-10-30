package gradlenew.adapterfeeds

import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VersionFeeds(private val baseUrl: URI = DEFAULT_BASE_URL, timeout: Duration = 20.seconds) {

    private val source = TextSource(timeout)

    fun fetchAll(): String = source.get(baseUrl.resolve("all"), "The versions feed", "application/json")

    companion object {
        val DEFAULT_BASE_URL: URI = URI("https://services.gradle.org/versions/")
    }
}
