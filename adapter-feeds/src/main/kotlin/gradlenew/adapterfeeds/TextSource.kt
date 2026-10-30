package gradlenew.adapterfeeds

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class SourceUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class TextSource(private val timeout: Duration) {

    private val client = HttpClient.newBuilder()
        .connectTimeout(timeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun get(url: URI, what: String, accept: String): String {
        val request = HttpRequest.newBuilder(url).timeout(timeout.toJavaDuration()).header("Accept", accept).GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (failure: IOException) {
            throw SourceUnavailable("$what $url could not be read: ${failure.message ?: failure.javaClass.simpleName}", failure)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SourceUnavailable("Reading $what $url was interrupted", interrupted)
        }
        if (response.statusCode() != 200) {
            throw SourceUnavailable("$what $url answered ${response.statusCode()}")
        }
        return response.body()
    }
}
