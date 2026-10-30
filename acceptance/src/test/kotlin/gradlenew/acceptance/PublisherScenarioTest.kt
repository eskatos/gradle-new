package gradlenew.acceptance

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.verification.Check
import gradlenew.verification.ChecksumSource
import gradlenew.verification.Outcome
import gradlenew.verification.VersionReport
import gradlenew.versions.Checksums
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class PublisherScenarioTest {

    private val stable = System.getProperty("gradlenew.offered.stable") ?: error("gradlenew.offered.stable is not set")
    private val pinnedDistribution: String? = System.getProperty("gradlenew.offered.stable.distribution-sha256")
    private val pinnedWrapper: String? = System.getProperty("gradlenew.offered.stable.wrapper-sha256")
    private val distributionCache = System.getProperty("acceptance.distributionCache") ?: error("acceptance.distributionCache is not set")
    private val recordedStable = Checksums("21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607", "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98")
    private val recordedRc = Checksums("a068e9c506ea61501c689900aec9010eda810b112e682146b1ae4291df3dab6a", "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5")
    private val classpath = System.getProperty("acceptance.publisherClasspath") ?: error("acceptance.publisherClasspath is not set")
    private val builderSources = System.getProperty("acceptance.builderSources") ?: error("acceptance.builderSources is not set")
    private val versionCatalog = System.getProperty("acceptance.versionCatalog") ?: error("acceptance.versionCatalog is not set")
    private val workRoot = Files.createTempDirectory("publisher-scenario").toFile()

    @AfterTest
    fun deleteWorkRoot() {
        workRoot.deleteRecursively()
    }

    @Test
    fun publisherScenario() {
        proposesOfferedVersionChanges()
        val model = publishesTheRealStable()
        val javaTarget = model.buildTypes.firstOrNull { it.supportsJavaVersion && it.componentType == "library" }
            ?: model.buildTypes.first { it.supportsJavaVersion }
        refusesADistributionWithAnotherChecksum()
        refusesNondeterministicOutput(model)
        refusesAnUnknownProjectNameForm()
        enumeratesATypeWhoseOutputDependsOnTheJavaVersion(model, javaTarget.id)
        refusesTemplatesOverTheBudget(javaTarget.id)
    }

    private fun proposesOfferedVersionChanges() {
        RecordedFeeds().use { feeds ->
            val stable982 = PinnedSlot("9.8.2", 25)
            val previous8146 = PinnedSlot("8.14.6", 21)

            val unchanged = propose(feeds, "unchanged", pinText(stable982, null, previous8146))
            assertEquals(emptyList(), unchanged.proposals())
            assertEquals(pinText(stable982, null, previous8146), unchanged.pinText())
            assertEquals(emptyMap(), unchanged.proposalDirectories())

            val activeRc = propose(feeds, "active-rc", pinText(stable982, null, previous8146))
            assertEquals(listOf("rc" to "Offer Gradle 9.9.0-rc-1"), activeRc.proposals())
            assertEquals(pinText(stable982, PinnedSlot("9.9.0-rc-1", 25, recordedRc), previous8146), activeRc.pinText())

            val retired = propose(feeds, "final-retires-rc", pinText(stable982, PinnedSlot("9.9.0-rc-2", 25), previous8146))
            assertEquals(listOf("stable" to "Offer Gradle 9.9.0"), retired.proposals())
            assertEquals(pinText(PinnedSlot("9.9.0", 25, recordedStable), null, previous8146), retired.pinText())

            val both = propose(feeds, "new-stable-and-rc", pinText(stable982, null, previous8146))
            assertEquals(listOf("stable" to "Offer Gradle 9.9.0", "rc" to "Offer Gradle 9.10.0-rc-1"), both.proposals())
            assertEquals(
                pinText(PinnedSlot("9.9.0", 25, recordedStable), PinnedSlot("9.10.0-rc-1", 25, recordedRc), previous8146),
                both.pinText(),
                "the written pin carries the checksums from the feed and the Java from the compatibility pages",
            )
            assertEquals(
                mapOf(
                    "stable" to Triple("offer-gradle/stable\n", "Offer Gradle 9.9.0\n", pinText(PinnedSlot("9.9.0", 25, recordedStable), null, previous8146)),
                    "rc" to Triple("offer-gradle/rc\n", "Offer Gradle 9.10.0-rc-1\n", pinText(stable982, PinnedSlot("9.10.0-rc-1", 25, recordedRc), previous8146)),
                ),
                both.proposalDirectories(),
                "each proposal gets a directory with its branch, title and the pin of that proposal alone",
            )

            assertEquals(emptyList(), propose(feeds, "broken-stable", pinText(stable982, null, previous8146)).proposals())
            val inactive = propose(feeds, "inactive-rc", pinText(stable982, PinnedSlot("9.9.0-rc-1", 25), previous8146))
            assertEquals(listOf("rc" to "Stop offering Gradle 9.9.0-rc-1"), inactive.proposals())
            assertEquals(pinText(stable982, null, previous8146), inactive.pinText())
            assertEquals(emptyList(), propose(feeds, "inactive-rc", pinText(stable982, null, previous8146)).proposals())
            assertEquals(emptyList(), propose(feeds, "milestone-and-nightly", pinText(stable982, null, previous8146)).proposals())
            assertEquals(emptyList(), propose(feeds, "older-stable", pinText(stable982, null, previous8146)).proposals())

            val patch = propose(feeds, "previous-patch", pinText(stable982, null, previous8146))
            assertEquals(listOf("previous" to "Offer Gradle 8.14.7 as previous major"), patch.proposals(), "old-major RCs, milestones, nightlies and broken releases are ignored")
            assertEquals(pinText(stable982, null, PinnedSlot("8.14.7", 21, Checksums("5".repeat(64), "6".repeat(64)))), patch.pinText())

            val major = propose(feeds, "major-bump", pinText(stable982, null, previous8146))
            assertEquals(listOf("stable" to "Offer Gradle 10.0.0 with 9.9.1 as previous major"), major.proposals())
            assertEquals(
                pinText(PinnedSlot("10.0.0", 25, Checksums("7".repeat(64), "8".repeat(64))), null, PinnedSlot("9.9.1", 25, Checksums("9".repeat(64), "a".repeat(64)))),
                major.pinText(),
                "a new major moves the previous major in the same proposal",
            )

            val unreadable = propose(feeds, "unreadable-docs", pinText(stable982, null, previous8146), expectSuccess = false)
            assertEquals(1, unreadable.exitCode, unreadable.output)
            assertTrue(unreadable.output.contains("compatibility"), unreadable.output)
            assertEquals(emptyList(), unreadable.proposals())
            assertEquals(pinText(stable982, null, previous8146), unreadable.pinText())
        }

        val unreachable = propose(null, "unreachable", pinText(PinnedSlot("9.8.2", 25), PinnedSlot("9.9.0-rc-1", 25), PinnedSlot("8.14.6", 21)), expectSuccess = false)
        assertEquals(1, unreachable.exitCode, unreachable.output)
        assertTrue(unreachable.output.contains("feed"), unreachable.output)
        assertEquals(pinText(PinnedSlot("9.8.2", 25), PinnedSlot("9.9.0-rc-1", 25), PinnedSlot("8.14.6", 21)), unreachable.pinText())
    }

    private data class PinnedSlot(val version: String, val java: Int, val checksums: Checksums? = null)

    private fun pinText(stable: PinnedSlot, rc: PinnedSlot?, previous: PinnedSlot): String =
        listOf("stable" to stable, "rc" to rc, "previous" to previous).joinToString("") { (slot, pinned) ->
            "$slot=${pinned?.version ?: ""}\n" +
                "$slot.distribution-sha256=${pinned?.checksums?.distribution ?: ""}\n" +
                "$slot.wrapper-sha256=${pinned?.checksums?.wrapper ?: ""}\n" +
                "$slot.daemon-java=${pinned?.java ?: ""}\n"
        }

    private fun propose(feeds: RecordedFeeds?, case: String, pin: String, expectSuccess: Boolean = true): ProposeRun {
        feeds?.serve(case)
        val pinFile = workRoot.resolve("propose/$case-${System.nanoTime()}/offered-gradle-versions.properties")
        pinFile.parentFile.mkdirs()
        pinFile.writeText(pin)
        val feedsUrl = feeds?.url ?: "http://127.0.0.1:${unusedPort()}/versions/"
        val docsUrl = feeds?.docsUrl ?: "http://127.0.0.1:${unusedPort()}/docs/"
        val proposalsDir = pinFile.resolveSibling("proposals")
        val arguments = listOf("propose", "--pin", pinFile.path, "--feeds-url", feedsUrl, "--docs-url", docsUrl, "--write", "--proposals-dir", proposalsDir.path)
        val process = ProcessBuilder(listOf(java(), "-Xmx256m", "-cp", classpath, "gradlenew.publisher.MainKt") + arguments).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "propose did not finish")
        val run = ProposeRun(process.exitValue(), stdout, "$stdout\n$stderr", pinFile, proposalsDir)
        if (expectSuccess) assertEquals(0, run.exitCode, "propose for '$case' failed:\n${run.output}")
        return run
    }

    private class ProposeRun(val exitCode: Int, val stdout: String, val output: String, private val pinFile: File, private val proposalsDir: File) {
        fun proposals(): List<Pair<String, String>> =
            Regex("\"slot\"\\s*:\\s*\"([a-z]+)\"[\\s\\S]*?\"title\"\\s*:\\s*\"([^\"]*)\"").findAll(stdout).map { it.groupValues[1] to it.groupValues[2] }.toList()
        fun pinText(): String = pinFile.readText()
        fun proposalDirectories(): Map<String, Triple<String, String, String>> =
            (proposalsDir.listFiles() ?: emptyArray()).associate { slot ->
                slot.name to Triple(slot.resolve("branch").readText(), slot.resolve("title").readText(), slot.resolve("offered-gradle-versions.properties").readText())
            }
    }

    private class RecordedFeeds : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var case: String = "unchanged"
        val url: String get() = "http://127.0.0.1:${server.address.port}/versions/"
        val docsUrl: String get() = "http://127.0.0.1:${server.address.port}/docs/"

        init {
            server.createContext("/versions/all") { exchange ->
                val body = requireNotNull(javaClass.getResource("/feeds/$case/all.json")) { "missing feed fixture $case/all.json" }.readBytes()
                respond(exchange, "application/json", body)
            }
            server.createContext("/docs/") { exchange ->
                val version = exchange.requestURI.path.removePrefix("/docs/").substringBefore('/')
                val page = when {
                    case == "unreadable-docs" -> "<html><body><p>This page has moved.</p></body></html>".toByteArray()
                    version.startsWith("8.") -> recordedPage("8.14.6")
                    else -> recordedPage("9.8.2")
                }
                respond(exchange, "text/html", page)
            }
            server.start()
        }

        private fun recordedPage(version: String): ByteArray =
            requireNotNull(javaClass.getResource("/docs/compatibility-$version.html")) { "missing compatibility page $version" }.readBytes()

        private fun respond(exchange: HttpExchange, contentType: String, body: ByteArray) {
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        fun serve(case: String) {
            this.case = case
        }

        override fun close() = server.stop(0)
    }

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    private fun java(): String = File(System.getProperty("java.home"), "bin/java").path

    private fun publishesTheRealStable(): InitModel {
        val run = publish("real-stable")

        assertEquals(0, run.exitCode, run.output)
        val report = run.report()
        assertEquals(Check.entries.toSet(), report.checks.map { it.check }.toSet())
        assertTrue(report.checks.all { it.outcome == Outcome.PASSED }, report.toString())
        assertEquals(if (pinnedDistribution != null) ChecksumSource.PINNED else ChecksumSource.PUBLISHED, report.checksums)

        assertEquals(listOf("v"), run.site.list()!!.toList())
        val model = InitModelJson.decode(run.site.resolve("v/$stable/model.json").readText())
        val enumerated = report.javaEnumeratedTypes.toSet()
        val expectedKeys = TemplateCatalog.of(model, enumerated).map { "${it.spec.key}.zip" }.toSet()
        assertEquals(expectedKeys, run.templateKeys())
        assertEquals(enumerated, TemplateIndexJson.decode(run.templates.resolve("$stable/index.json").readText()).javaEnumeratedTypes)
        return model
    }

    private fun refusesADistributionWithAnotherChecksum() {
        val run = publish("wrong-checksum", distributionSha256 = "0".repeat(64))

        assertEquals(1, run.exitCode, run.output)
        val distribution = run.report().result(Check.DISTRIBUTION)
        assertEquals(Outcome.FAILED, distribution.outcome)
        assertTrue(distribution.failures.single().message.endsWith("expected ${"0".repeat(64)}"), distribution.toString())
        assertTrue(run.report().checks.drop(1).all { it.outcome == Outcome.SKIPPED }, run.report().toString())
        run.assertNothingPublished()
    }

    private fun refusesNondeterministicOutput(model: InitModel) {
        val run = publish("nondeterministic", initScript = "nondeterministic.gradle")

        assertEquals(1, run.exitCode, run.output)
        val determinism = run.report().result(Check.DETERMINISM)
        assertEquals(Outcome.FAILED, determinism.outcome)
        val typeIds = model.buildTypes.map { it.id }
        assertTrue(determinism.failures.isNotEmpty() && determinism.failures.all { failure -> typeIds.any { failure.subject.startsWith("$it.") } }, determinism.toString())
        assertTrue(determinism.failures.all { it.message.contains("generated-at.txt") }, determinism.toString())
        run.assertNothingPublished()
    }

    private fun refusesAnUnknownProjectNameForm() {
        val run = publish("unknown-name-form", initScript = "spaced-project-name.gradle")

        assertEquals(1, run.exitCode, run.output)
        val report = run.report()
        assertEquals(Outcome.FAILED, report.result(Check.HYGIENE).outcome)
        assertTrue(report.result(Check.HYGIENE).failures.any { it.message.contains("PROJECT.txt") }, report.toString())
        assertEquals(Outcome.FAILED, report.result(Check.DIFFERENTIAL).outcome)
        run.assertNothingPublished()
    }

    private fun enumeratesATypeWhoseOutputDependsOnTheJavaVersion(model: InitModel, target: String) {
        val run = publish("java-version-elsewhere", initScript = "java-version-comment.gradle", faultTarget = target)

        assertEquals(0, run.exitCode, run.output)
        val report = run.report()
        assertTrue(report.checks.all { it.outcome == Outcome.PASSED }, report.toString())
        assertEquals(listOf(target), report.javaEnumeratedTypes)
        assertEquals(setOf(target), TemplateIndexJson.decode(run.templates.resolve("$stable/index.json").readText()).javaEnumeratedTypes)

        val keys = run.templateKeys()
        val range = model.javaVersion.minimum..model.javaVersion.maximum
        val targetKeys = keys.filter { it.startsWith("$target.") }
        val combinations = TemplateCatalog.of(model).count { it.spec.type == target }
        assertEquals(combinations * (range.last - range.first + 1), targetKeys.size)
        assertEquals(range.toSet(), targetKeys.map { it.removeSuffix(".zip").substringAfterLast(".java").toInt() }.toSet())
        assertTrue(keys.filterNot { it.startsWith("$target.") }.none { it.contains(".java") }, keys.toString())
    }

    private fun refusesTemplatesOverTheBudget(target: String) {
        val run = publish("over-budget", initScript = "java-version-comment.gradle", faultTarget = target, budgetMegabytes = 1)

        assertEquals(1, run.exitCode, run.output)
        assertEquals(Outcome.FAILED, run.report().result(Check.BUDGET).outcome)
        run.assertNothingPublished()
    }

    private fun publish(
        name: String,
        initScript: String? = null,
        faultTarget: String? = null,
        budgetMegabytes: Int? = null,
        distributionSha256: String? = pinnedDistribution,
    ): PublisherRun {
        val dir = workRoot.resolve(name)
        val arguments = buildList {
            addAll(listOf("publish", "--version", stable))
            addAll(listOf("--builder-dir", builderSources, "--catalog", versionCatalog))
            addAll(listOf("--output", dir.resolve("site").path, "--templates-output", dir.resolve("templates").path))
            addAll(listOf("--report-output", dir.resolve("report").path, "--work-dir", dir.resolve("work").path))
            addAll(listOf("--distribution-cache", distributionCache))
            if (distributionSha256 != null) {
                addAll(listOf("--distribution-sha256", distributionSha256, "--wrapper-sha256", pinnedWrapper ?: error("the pinned wrapper SHA-256 is missing")))
            }
            budgetMegabytes?.let { addAll(listOf("--template-budget-mb", it.toString())) }
            initScript?.let { addAll(listOf("--gradle-argument", "--init-script", "--gradle-argument", faultInjection(it).path)) }
            faultTarget?.let { addAll(listOf("--gradle-argument", "-Dgradlenew.fault.type=$it")) }
        }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val started = TimeSource.Monotonic.markNow()
        val process = ProcessBuilder(listOf(java, "-Xmx512m", "-cp", classpath, "gradlenew.publisher.MainKt") + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(20, TimeUnit.MINUTES), "publisher did not finish")
        println("publisher scenario step '$name': exit ${process.exitValue()} in ${started.elapsedNow().inWholeSeconds} s")
        return PublisherRun(process.exitValue(), output, dir)
    }

    private fun faultInjection(name: String): File {
        val target = workRoot.resolve("fault-injection/$name")
        target.parentFile.mkdirs()
        target.writeText(requireNotNull(javaClass.getResource("/fault-injection/$name")) { "missing init script $name" }.readText())
        return target
    }

    private inner class PublisherRun(val exitCode: Int, val output: String, dir: File) {
        val site = dir.resolve("site")
        val templates = dir.resolve("templates")
        private val reportFile = dir.resolve("report/verification-report.json")

        fun report(): VersionReport {
            assertTrue(reportFile.isFile, "no verification report was written:\n$output")
            return VerificationReportJson.decode(reportFile.readText()).versions.single { it.version == stable }
        }

        fun templateKeys(): Set<String> =
            templates.resolve(stable).list()!!.filter { it.endsWith(".zip") }.toSet()

        fun assertNothingPublished() {
            assertFalse(site.walkTopDown().any { it.isFile }, "site files were published:\n$output")
            assertFalse(templates.walkTopDown().any { it.isFile }, "templates were published:\n$output")
        }
    }

    private fun VersionReport.result(check: Check) = checks.single { it.check == check }
}
