package gradlenew.adapterfeeds

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompatibilityPagesTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val baseUrl = URI("http://127.0.0.1:${server.address.port}/docs/")
    private val requested = mutableListOf<String>()

    @AfterTest
    fun stop() = server.stop(0)

    private fun serve(status: Int, body: String) {
        server.createContext("/docs/") { exchange ->
            requested += exchange.requestURI.path
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun fetchesTheCompatibilityPageOfAVersionFromItsOwnManual() {
        serve(200, "<table>Support for running Gradle</table>")

        assertEquals("<table>Support for running Gradle</table>", CompatibilityPages(baseUrl).fetch("8.14.6"))
        CompatibilityPages(baseUrl).fetch("9.9.0-rc-1")
        assertEquals(listOf("/docs/8.14.6/userguide/compatibility.html", "/docs/9.9.0-rc-1/userguide/compatibility.html"), requested)
    }

    @Test
    fun reportsAMissingPageWithItsUrl() {
        serve(404, "not found")

        val failure = assertFailsWith<SourceUnavailable> { CompatibilityPages(baseUrl).fetch("8.14.6") }
        assertTrue(failure.message!!.contains("${baseUrl}8.14.6/userguide/compatibility.html"), failure.message)
        assertTrue(failure.message!!.contains("404"), failure.message)
    }

    @Test
    fun onlyFetchesPagesOfVersionNumbers() {
        assertFailsWith<IllegalArgumentException> { CompatibilityPages(baseUrl).fetch("../../etc") }
        assertFailsWith<IllegalArgumentException> { CompatibilityPages(baseUrl).fetch("9.8.2?x=1") }
        assertEquals(emptyList(), requested)
    }

    @Test
    fun defaultsToDocsGradleOrg() {
        assertEquals(URI("https://docs.gradle.org/"), CompatibilityPages.DEFAULT_BASE_URL)
    }
}
