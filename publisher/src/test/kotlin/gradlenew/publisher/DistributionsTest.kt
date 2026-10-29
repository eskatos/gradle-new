package gradlenew.publisher

import com.sun.net.httpserver.HttpServer
import gradlenew.verification.ChecksumSource
import gradlenew.verification.Outcome
import gradlenew.versions.Checksums
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DistributionsTest {

    private val zip = "a fake Gradle distribution".encodeToByteArray()
    private val wrapper = "a fake wrapper".encodeToByteArray()
    private val pinned = Checksums(Sha256.hex(zip), Sha256.hex(wrapper))
    private val downloads = AtomicInteger()
    private var served = zip
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/distributions/") { exchange ->
            val body = when (exchange.requestURI.path.substringAfterLast('/')) {
                "gradle-9.8.2-bin.zip" -> served.also { downloads.incrementAndGet() }
                "gradle-9.8.2-bin.zip.sha256" -> pinned.distribution.encodeToByteArray()
                "gradle-9.8.2-wrapper.jar.sha256" -> "${pinned.wrapper}\n".encodeToByteArray()
                else -> null
            }
            if (body == null) exchange.sendResponseHeaders(404, -1) else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        start()
    }
    private val cache: File = Files.createTempDirectory("distributions").toFile()
    private val distributions = Distributions(cache, URI("http://127.0.0.1:${server.address.port}/distributions/"))

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun downloadsVerifiesAndCachesAPinnedDistribution() {
        val first = assertIs<DistributionOutcome.Verified>(distributions.obtain("9.8.2", pinned)).distribution
        assertEquals(pinned, first.checksums)
        assertEquals(ChecksumSource.PINNED, first.source)
        assertTrue(File(first.zip).readBytes().contentEquals(zip))
        assertEquals(cache, File(first.zip).parentFile)

        val second = assertIs<DistributionOutcome.Verified>(distributions.obtain("9.8.2", pinned)).distribution
        assertEquals(first, second)
        assertEquals(1, downloads.get(), "a cached zip that still matches is reused")
    }

    @Test
    fun refusesADistributionWithAnotherChecksumAndKeepsNothing() {
        served = "a tampered distribution".encodeToByteArray()

        val rejected = assertIs<DistributionOutcome.Rejected>(distributions.obtain("9.8.2", pinned))

        assertEquals(Outcome.FAILED, rejected.check.outcome)
        assertEquals("SHA-256 is ${Sha256.hex(served)}, expected ${pinned.distribution}", rejected.check.failures.single().message)
        assertFalse(cache.walkTopDown().any { it.isFile }, cache.walkTopDown().toList().toString())
    }

    @Test
    fun downloadsAgainWhenTheCachedZipNoLongerMatches() {
        distributions.obtain("9.8.2", pinned)
        File(cache, "gradle-9.8.2-bin.zip").writeText("corrupted")

        assertIs<DistributionOutcome.Verified>(distributions.obtain("9.8.2", pinned))

        assertEquals(2, downloads.get())
        assertTrue(File(cache, "gradle-9.8.2-bin.zip").readBytes().contentEquals(zip))
    }

    @Test
    fun usesThePublishedChecksumsWhenNoneArePinned() {
        val verified = assertIs<DistributionOutcome.Verified>(distributions.obtain("9.8.2", null)).distribution

        assertEquals(pinned, verified.checksums)
        assertEquals(ChecksumSource.PUBLISHED, verified.source)
    }

    @Test
    fun reportsADistributionThatCannotBeDownloaded() {
        val rejected = assertIs<DistributionOutcome.Rejected>(distributions.obtain("9.9.9", null))

        assertTrue(rejected.check.failures.single().message.startsWith("the distribution could not be obtained:"), rejected.check.toString())
    }
}
