package gradlenew.adapterfeeds

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class VersionFeedsTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val baseUrl = URI("http://127.0.0.1:${server.address.port}/versions/")

    @AfterTest
    fun stop() = server.stop(0)

    private fun serve(path: String, status: Int, body: String, delayMillis: Long = 0) {
        server.createContext(path) { exchange ->
            Thread.sleep(delayMillis)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun fetchesTheListOfAllVersionsAsItIs() {
        serve("/versions/all", 200, "[ { \"version\" : \"9.8.2\", \"current\" : true } ]")

        assertEquals("[ { \"version\" : \"9.8.2\", \"current\" : true } ]", VersionFeeds(baseUrl).fetchAll())
    }

    @Test
    fun reportsANonSuccessfulResponseWithTheUrlAndStatus() {
        serve("/versions/all", 503, "unavailable")

        val failure = assertFailsWith<SourceUnavailable> { VersionFeeds(baseUrl).fetchAll() }
        assertTrue(failure.message!!.contains("${baseUrl}all"), failure.message)
        assertTrue(failure.message!!.contains("503"), failure.message)
    }

    @Test
    fun reportsAnUnreachableServer() {
        val closedPort = ServerSocket(0).use { it.localPort }

        val failure = assertFailsWith<SourceUnavailable> { VersionFeeds(URI("http://127.0.0.1:$closedPort/versions/")).fetchAll() }
        assertTrue(failure.message!!.contains("127.0.0.1:$closedPort"), failure.message)
    }

    @Test
    fun givesUpAfterTheTimeout() {
        serve("/versions/all", 200, "[ ]", delayMillis = 2_000)

        assertFailsWith<SourceUnavailable> { VersionFeeds(baseUrl, timeout = 300.milliseconds).fetchAll() }
    }

    @Test
    fun defaultsToServicesGradleOrg() {
        assertEquals(URI("https://services.gradle.org/versions/"), VersionFeeds.DEFAULT_BASE_URL)
    }
}
