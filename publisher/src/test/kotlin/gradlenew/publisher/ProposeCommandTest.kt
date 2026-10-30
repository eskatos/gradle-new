package gradlenew.publisher

import gradlenew.adapterfeeds.SourceUnavailable
import gradlenew.adapterjson.ProposalsJson
import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.PinnedVersion
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
    private val pinText = "stable=9.8.2\nstable.daemon-java=25\nrc=\nprevious=8.14.6\nprevious.daemon-java=21\n"
    private val pinFile = dir.resolve("offered-gradle-versions.properties").apply { writeText(pinText) }
    private val stable982 = PinnedVersion("9.8.2", 25)
    private val previous8146 = PinnedVersion("8.14.6", 21)
    private val stable990 = Checksums("1".repeat(64), "2".repeat(64))
    private val rc9100 = Checksums("3".repeat(64), "4".repeat(64))
    private val previous8147 = Checksums("5".repeat(64), "6".repeat(64))

    private val feed = """
        [
          { "version" : "9.9.0", "current" : true, "checksum" : "${stable990.distribution}", "wrapperChecksum" : "${stable990.wrapper}" },
          { "version" : "9.10.0-rc-1", "activeRc" : true, "rcFor" : "9.10.0", "checksum" : "${rc9100.distribution}", "wrapperChecksum" : "${rc9100.wrapper}" },
          { "version" : "9.8.2" },
          { "version" : "8.14.7", "checksum" : "${previous8147.distribution}", "wrapperChecksum" : "${previous8147.wrapper}" },
          { "version" : "8.14.6" }
        ]
    """.trimIndent()
    private val unchangedFeed = """[ { "version" : "9.8.2", "current" : true }, { "version" : "8.14.6" } ]"""

    private val page = """
        <table><thead><tr><th>Java version</th><th>Support for toolchains</th><th>Support for running Gradle</th></tr></thead>
        <tbody>
        <tr><td><p>21</p></td><td><p>8.4</p></td><td><p>8.5 and after</p></td></tr>
        <tr><td><p>24</p></td><td><p>8.14</p></td><td><p>8.14 and after</p></td></tr>
        <tr><td><p>25</p></td><td><p>9.0.0</p></td><td><p>9.1.0 and after</p></td></tr>
        </tbody></table>
    """.trimIndent()

    private class Run(val exitCode: Int, val stdout: String, val stderr: String)

    private val pagesAsked = mutableListOf<Pair<URI, String>>()

    private fun propose(arguments: List<String>, feed: String = this.feed, page: (String) -> String = { this.page }, fetchFeed: ((URI) -> String)? = null): Run {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val command = ProposeCommand(fetchFeed ?: { feed }) { docs, version -> pagesAsked += docs to version; page(version) }
        val exitCode = command.run(arguments, PrintStream(stdout, true), PrintStream(stderr, true))
        return Run(exitCode, stdout.toString(), stderr.toString())
    }

    @Test
    fun printsTheProposalsAsJsonWithoutTouchingThePin() {
        val run = propose(listOf("--pin", pinFile.path))

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin(PinnedVersion("9.9.0", 25, stable990), previous8146)),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin(stable982, previous8146, PinnedVersion("9.10.0-rc-1", 25, rc9100))),
                Proposal(Slot.PREVIOUS, "Offer Gradle 8.14.7 as previous major", Pin(stable982, PinnedVersion("8.14.7", 21, previous8147))),
            ),
            ProposalsJson.decode(run.stdout),
        )
        assertEquals(pinText, pinFile.readText())
    }

    @Test
    fun writesTheCombinedOfferWhenAsked() {
        val run = propose(listOf("--pin", pinFile.path, "--write"))

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(
            Pin(PinnedVersion("9.9.0", 25, stable990), PinnedVersion("8.14.7", 21, previous8147), PinnedVersion("9.10.0-rc-1", 25, rc9100)),
            PinFile.read(pinFile),
            "the written pin carries the feed's checksums and the Java of each version",
        )
    }

    @Test
    fun readsTheCompatibilityPageOnlyForVersionsEnteringThePin() {
        propose(listOf("--pin", pinFile.path))
        assertEquals(setOf("9.9.0", "9.10.0-rc-1", "8.14.7"), pagesAsked.map { it.second }.toSet())
        pagesAsked.clear()
        propose(listOf("--pin", pinFile.path), feed = unchangedFeed)
        assertEquals(emptyList(), pagesAsked)
    }

    @Test
    fun leavesTheFileAloneWhenNothingChanges() {
        pinFile.writeText("# kept as written\n$pinText")
        val run = propose(listOf("--pin", pinFile.path, "--write"), feed = unchangedFeed)

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(emptyList(), ProposalsJson.decode(run.stdout))
        assertEquals("# kept as written\n$pinText", pinFile.readText())
    }

    @Test
    fun usesTheGivenUrlsAndGradleOrgByDefault() {
        val feedsAsked = mutableListOf<URI>()
        propose(listOf("--pin", pinFile.path)) { feedsAsked += it; feed }
        propose(listOf("--pin", pinFile.path, "--feeds-url", "http://127.0.0.1:9/versions/", "--docs-url", "http://127.0.0.1:9/docs/")) { feedsAsked += it; feed }

        assertEquals(listOf(URI("https://services.gradle.org/versions/"), URI("http://127.0.0.1:9/versions/")), feedsAsked)
        assertEquals(setOf(URI("https://docs.gradle.org/"), URI("http://127.0.0.1:9/docs/")), pagesAsked.map { it.first }.toSet())
    }

    @Test
    fun exitsOneOnAnUnavailableSourceAndLeavesThePinUnchanged() {
        val feedDown = propose(listOf("--pin", pinFile.path, "--write")) { throw SourceUnavailable("The versions feed x answered 503") }
        assertEquals(1, feedDown.exitCode)
        assertTrue(feedDown.stderr.contains("The versions feed x answered 503"), feedDown.stderr)
        assertEquals("", feedDown.stdout)

        val pageDown = propose(listOf("--pin", pinFile.path, "--write"), page = { throw SourceUnavailable("The compatibility page y answered 404") })
        assertEquals(1, pageDown.exitCode)
        assertTrue(pageDown.stderr.contains("The compatibility page y answered 404"), pageDown.stderr)
        assertEquals(pinText, pinFile.readText())
    }

    @Test
    fun exitsOneOnACompatibilityPageItCannotReadAndLeavesThePinUnchanged() {
        val run = propose(listOf("--pin", pinFile.path, "--write"), page = { "<html><body><p>This page has moved.</p></body></html>" })

        assertEquals(1, run.exitCode)
        assertTrue(run.stderr.contains("compatibility"), run.stderr)
        assertEquals("", run.stdout)
        assertEquals(pinText, pinFile.readText())
    }

    @Test
    fun exitsOneOnAMalformedFeedPinOrCommandLine() {
        assertEquals(1, propose(listOf("--pin", pinFile.path), feed = "not json").exitCode)
        assertEquals(1, propose(listOf("--pin", pinFile.path), feed = """[ { "version" : "9.8.2" } ]""").exitCode)
        assertEquals(1, propose(listOf("--write")).exitCode)
        assertEquals(1, propose(listOf("--pin", pinFile.path, "--unknown")).exitCode)
        pinFile.writeText("rc=\n")
        assertEquals(1, propose(listOf("--pin", pinFile.path)).exitCode)
    }
}
