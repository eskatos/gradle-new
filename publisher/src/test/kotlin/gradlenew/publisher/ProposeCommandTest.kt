package gradlenew.publisher

import gradlenew.adapterfeeds.FeedUnavailable
import gradlenew.adapterfeeds.RawFeeds
import gradlenew.adapterjson.ProposalsJson
import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProposeCommandTest {

    private val dir = Files.createTempDirectory("propose").toFile()
    private val pinFile = dir.resolve("offered-gradle-versions.properties").apply { writeText("stable=9.8.2\nrc=\n") }
    private val final990 = """{ "version" : "9.9.0", "activeRc" : false, "rcFor" : "", "broken" : false }"""
    private val rc9100 = """{ "version" : "9.10.0-rc-1", "activeRc" : true, "rcFor" : "9.10.0", "broken" : false }"""

    private class Run(val exitCode: Int, val stdout: String, val stderr: String)

    private fun propose(arguments: List<String>, fetch: (URI) -> RawFeeds): Run {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val exitCode = ProposeCommand(fetch).run(arguments, PrintStream(stdout, true), PrintStream(stderr, true))
        return Run(exitCode, stdout.toString(), stderr.toString())
    }

    @Test
    fun printsTheProposalsAsJsonWithoutTouchingThePin() {
        val run = propose(listOf("--pin", pinFile.path)) { RawFeeds(final990, rc9100) }

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null)),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin("9.8.2", "9.10.0-rc-1")),
            ),
            ProposalsJson.decode(run.stdout),
        )
        assertEquals("stable=9.8.2\nrc=\n", pinFile.readText())
    }

    @Test
    fun writesTheCombinedOfferWhenAsked() {
        val run = propose(listOf("--pin", pinFile.path, "--write")) { RawFeeds(final990, rc9100) }

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals("stable=9.9.0\nrc=9.10.0-rc-1\n", pinFile.readText())
    }

    @Test
    fun leavesTheFileAloneWhenNothingChanges() {
        pinFile.writeText("# kept as written\nstable=9.8.2\n")
        val run = propose(listOf("--pin", pinFile.path, "--write")) { RawFeeds("""{ "version" : "9.8.2" }""", "{ }") }

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(emptyList(), ProposalsJson.decode(run.stdout))
        assertEquals("# kept as written\nstable=9.8.2\n", pinFile.readText())
    }

    @Test
    fun usesTheGivenFeedsUrlAndServicesGradleOrgByDefault() {
        val asked = mutableListOf<URI>()
        propose(listOf("--pin", pinFile.path)) { asked += it; RawFeeds("{ }", "{ }") }
        propose(listOf("--pin", pinFile.path, "--feeds-url", "http://127.0.0.1:9/versions/")) { asked += it; RawFeeds("{ }", "{ }") }

        assertEquals(listOf(URI("https://services.gradle.org/versions/"), URI("http://127.0.0.1:9/versions/")), asked)
    }

    @Test
    fun exitsOneOnAFeedFailureAndLeavesThePinUnchanged() {
        val run = propose(listOf("--pin", pinFile.path, "--write")) { throw FeedUnavailable("The versions feed x answered 503") }

        assertEquals(1, run.exitCode)
        assertTrue(run.stderr.contains("The versions feed x answered 503"), run.stderr)
        assertEquals("", run.stdout)
        assertEquals("stable=9.8.2\nrc=\n", pinFile.readText())
    }

    @Test
    fun exitsOneOnAMalformedFeedPinOrCommandLine() {
        assertEquals(1, propose(listOf("--pin", pinFile.path)) { RawFeeds("not json", "{ }") }.exitCode)
        pinFile.writeText("rc=\n")
        assertEquals(1, propose(listOf("--pin", pinFile.path)) { RawFeeds("{ }", "{ }") }.exitCode)
        assertEquals(1, propose(listOf("--write")) { RawFeeds("{ }", "{ }") }.exitCode)
        assertEquals(1, propose(listOf("--pin", pinFile.path, "--unknown")) { RawFeeds("{ }", "{ }") }.exitCode)
    }
}
