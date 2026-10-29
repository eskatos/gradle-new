package gradlenew.publisher

import gradlenew.verification.CheckResult
import gradlenew.verification.ChecksumSource
import gradlenew.verification.DistributionCheck
import gradlenew.versions.Checksums
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

data class VerifiedDistribution(val version: String, val zip: URI, val checksums: Checksums, val source: ChecksumSource)

sealed interface DistributionOutcome {
    data class Verified(val distribution: VerifiedDistribution) : DistributionOutcome
    data class Rejected(val check: CheckResult) : DistributionOutcome
}

class Distributions(private val cache: File, private val baseUrl: URI = DEFAULT_BASE_URL) {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build()

    fun obtain(version: String, pinned: Checksums?): DistributionOutcome =
        try {
            val checksums = pinned ?: published(version)
            val source = if (pinned != null) ChecksumSource.PINNED else ChecksumSource.PUBLISHED
            cache.mkdirs()
            val zip = File(cache, "gradle-$version-bin.zip")
            if (zip.isFile && Sha256.of(zip) == checksums.distribution) {
                DistributionOutcome.Verified(VerifiedDistribution(version, zip.toURI(), checksums, source))
            } else {
                download(version, zip, checksums, source)
            }
        } catch (failure: IOException) {
            DistributionOutcome.Rejected(DistributionCheck.unavailable(version, failure.message ?: failure.toString()))
        } catch (failure: IllegalArgumentException) {
            DistributionOutcome.Rejected(DistributionCheck.unavailable(version, failure.message ?: failure.toString()))
        }

    private fun download(version: String, zip: File, checksums: Checksums, source: ChecksumSource): DistributionOutcome {
        val partial = File.createTempFile("gradle-$version-bin", ".zip.part", cache)
        try {
            val response = client.send(request("gradle-$version-bin.zip", Duration.ofMinutes(10)), HttpResponse.BodyHandlers.ofFile(partial.toPath()))
            if (response.statusCode() != 200) throw IOException("${response.uri()} answered ${response.statusCode()}")
            val check = DistributionCheck.of(version, checksums.distribution, Sha256.of(partial))
            if (check.failures.isNotEmpty()) return DistributionOutcome.Rejected(check)
            try {
                Files.move(partial.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return DistributionOutcome.Verified(VerifiedDistribution(version, zip.toURI(), checksums, source))
        } finally {
            partial.delete()
        }
    }

    private fun published(version: String): Checksums =
        Checksums(text("gradle-$version-bin.zip.sha256"), text("gradle-$version-wrapper.jar.sha256"))

    private fun text(name: String): String {
        val response = client.send(request(name, Duration.ofSeconds(30)), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw IOException("${response.uri()} answered ${response.statusCode()}")
        return response.body().trim().lowercase()
    }

    private fun request(name: String, timeout: Duration): HttpRequest =
        HttpRequest.newBuilder(baseUrl.resolve(name)).timeout(timeout).GET().build()

    companion object {
        val DEFAULT_BASE_URL: URI = URI("https://services.gradle.org/distributions/")
    }
}
