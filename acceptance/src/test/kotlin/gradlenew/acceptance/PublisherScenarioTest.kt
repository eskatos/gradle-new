package gradlenew.acceptance

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.ProposalsJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.verification.Check
import gradlenew.verification.ChecksumSource
import gradlenew.verification.Outcome
import gradlenew.verification.VersionReport
import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
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
            val unchanged = propose(feeds, "unchanged", Pin("9.8.2", null), write = true)
            assertEquals(emptyList(), unchanged.proposals())
            assertEquals("stable=9.8.2\nrc=\n", unchanged.pinText())

            assertEquals(
                listOf(Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-1", Pin("9.8.2", "9.9.0-rc-1", null, recordedRc))),
                propose(feeds, "active-rc", Pin("9.8.2", null)).proposals(),
            )
            assertEquals(
                listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null, recordedStable))),
                propose(feeds, "final-retires-rc", Pin("9.8.2", "9.9.0-rc-2")).proposals(),
            )

            val both = propose(feeds, "new-stable-and-rc", Pin("9.8.2", null), write = true)
            assertEquals(
                listOf(
                    Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null, recordedStable)),
                    Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin("9.8.2", "9.10.0-rc-1", null, recordedRc)),
                ),
                both.proposals(),
            )
            assertEquals(
                "stable=9.9.0\nstable.distribution-sha256=${recordedStable.distribution}\nstable.wrapper-sha256=${recordedStable.wrapper}\n" +
                    "rc=9.10.0-rc-1\nrc.distribution-sha256=${recordedRc.distribution}\nrc.wrapper-sha256=${recordedRc.wrapper}\n",
                both.pinText(),
                "the written pin carries the checksums from the feeds",
            )

            assertEquals(emptyList(), propose(feeds, "broken-stable", Pin("9.8.2", null)).proposals())
            assertEquals(
                listOf(Proposal(Slot.RC, "Stop offering Gradle 9.9.0-rc-1", Pin("9.8.2", null))),
                propose(feeds, "inactive-rc", Pin("9.8.2", "9.9.0-rc-1")).proposals(),
            )
            assertEquals(emptyList(), propose(feeds, "inactive-rc", Pin("9.8.2", null)).proposals())
            assertEquals(emptyList(), propose(feeds, "milestone-and-nightly", Pin("9.8.2", null)).proposals())
            assertEquals(emptyList(), propose(feeds, "older-stable", Pin("9.8.2", null)).proposals())
        }

        val unreachable = propose(null, "unreachable", Pin("9.8.2", "9.9.0-rc-1"), write = true)
        assertEquals(1, unreachable.exitCode, unreachable.output)
        assertTrue(unreachable.output.contains("feed"), unreachable.output)
        assertEquals("stable=9.8.2\nrc=9.9.0-rc-1\n", unreachable.pinText())
    }

    private fun propose(feeds: RecordedFeeds?, case: String, pin: Pin, write: Boolean = false): ProposeRun {
        feeds?.serve(case)
        val pinFile = workRoot.resolve("propose/$case-${System.nanoTime()}/offered-gradle-versions.properties")
        pinFile.parentFile.mkdirs()
        pinFile.writeText("stable=${pin.stable}\nrc=${pin.rc ?: ""}\n")
        val feedsUrl = feeds?.url ?: "http://127.0.0.1:${unusedPort()}/versions/"
        val arguments = listOf("propose", "--pin", pinFile.path, "--feeds-url", feedsUrl) + if (write) listOf("--write") else emptyList()
        val process = ProcessBuilder(listOf(java(), "-Xmx256m", "-cp", classpath, "gradlenew.publisher.MainKt") + arguments).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "propose did not finish")
        val run = ProposeRun(process.exitValue(), stdout, "$stdout\n$stderr", pinFile)
        if (feeds != null) assertEquals(0, run.exitCode, "propose for '$case' failed:\n${run.output}")
        return run
    }

    private class ProposeRun(val exitCode: Int, val stdout: String, val output: String, private val pinFile: File) {
        fun proposals(): List<Proposal> = ProposalsJson.decode(stdout)
        fun pinText(): String = pinFile.readText()
    }

    private class RecordedFeeds : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var case: String = "unchanged"
        val url: String get() = "http://127.0.0.1:${server.address.port}/versions/"

        init {
            for (feed in listOf("current", "release-candidate")) {
                server.createContext("/versions/$feed") { exchange ->
                    val body = requireNotNull(javaClass.getResource("/feeds/$case/$feed.json")) { "missing feed fixture $case/$feed" }.readBytes()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            server.start()
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
