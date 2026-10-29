package gradlenew.adapterfeeds

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class FeedUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class RawFeeds(val current: String, val releaseCandidate: String)

class VersionFeeds(private val baseUrl: URI = DEFAULT_BASE_URL, private val timeout: Duration = 20.seconds) {

    private val client = HttpClient.newBuilder()
        .connectTimeout(timeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(): RawFeeds = RawFeeds(get("current"), get("release-candidate"))

    private fun get(feed: String): String {
        val url = baseUrl.resolve(feed)
        val request = HttpRequest.newBuilder(url).timeout(timeout.toJavaDuration()).header("Accept", "application/json").GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (failure: IOException) {
            throw FeedUnavailable("The versions feed $url could not be read: ${failure.message ?: failure.javaClass.simpleName}", failure)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FeedUnavailable("Reading the versions feed $url was interrupted", interrupted)
        }
        if (response.statusCode() != 200) {
            throw FeedUnavailable("The versions feed $url answered ${response.statusCode()}")
        }
        return response.body()
    }

    companion object {
        val DEFAULT_BASE_URL: URI = URI("https://services.gradle.org/versions/")
    }
}
