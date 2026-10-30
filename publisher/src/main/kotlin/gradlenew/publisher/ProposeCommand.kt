package gradlenew.publisher

import gradlenew.adapterfeeds.CompatibilityPages
import gradlenew.adapterfeeds.SourceUnavailable
import gradlenew.adapterfeeds.VersionFeeds
import gradlenew.adapterjson.FeedJson
import gradlenew.adapterjson.ProposalsJson
import gradlenew.versions.Feeds
import gradlenew.versions.JavaCompatibility
import gradlenew.versions.OfferingPolicy
import gradlenew.versions.Proposal
import gradlenew.versions.Proposals
import java.io.File
import java.io.PrintStream
import java.net.URI

class ProposeCommand(
    private val fetchFeed: (URI) -> String = { VersionFeeds(it).fetchAll() },
    private val fetchPage: (URI, String) -> String = { docs, version -> CompatibilityPages(docs).fetch(version) },
) {

    fun run(arguments: List<String>, out: PrintStream, err: PrintStream): Int =
        try {
            propose(arguments, out, err)
            0
        } catch (failure: SourceUnavailable) {
            err.println("propose: ${failure.message}; the pin is unchanged")
            1
        } catch (failure: IllegalArgumentException) {
            err.println("propose: ${failure.message}")
            1
        }

    private fun propose(arguments: List<String>, out: PrintStream, err: PrintStream) {
        var pinPath: String? = null
        var feedsUrl = VersionFeeds.DEFAULT_BASE_URL
        var docsUrl = CompatibilityPages.DEFAULT_BASE_URL
        var write = false
        var proposalsDir: File? = null
        var i = 0
        while (i < arguments.size) {
            when (val option = arguments[i]) {
                "--pin" -> pinPath = arguments.getOrNull(++i)
                "--feeds-url" -> feedsUrl = URI(requireNotNull(arguments.getOrNull(++i)) { "--feeds-url needs a value" })
                "--docs-url" -> docsUrl = URI(requireNotNull(arguments.getOrNull(++i)) { "--docs-url needs a value" })
                "--write" -> write = true
                "--proposals-dir" -> proposalsDir = File(requireNotNull(arguments.getOrNull(++i)) { "--proposals-dir needs a value" })
                else -> throw IllegalArgumentException("unknown option '$option'; $USAGE")
            }
            i++
        }
        val pinFile = File(requireNotNull(pinPath) { "missing --pin <file>; $USAGE" })
        proposalsDir?.let { require(!it.exists() || it.list()?.isEmpty() == true) { "--proposals-dir ${it.path} must be empty or absent" } }
        val pin = PinFile.read(pinFile)
        val feeds = Feeds(FeedJson.decode(fetchFeed(feedsUrl)))
        val offered = OfferingPolicy.offer(feeds, pin) { version ->
            JavaCompatibility.highestLts(fetchPage(docsUrl, version), version).also {
                err.println("propose: Java $it runs Gradle $version, from its compatibility page")
            }
        }
        val proposals = Proposals.of(pin, offered)
        out.println(ProposalsJson.encode(proposals))
        proposalsDir?.let { writeProposals(it, proposals) }
        if (proposals.isEmpty()) {
            err.println("propose: the pin matches the feed, nothing to propose")
        } else {
            proposals.forEach { err.println("propose: ${it.title} (${it.branch})") }
            if (write) {
                PinFile.write(pinFile, offered)
                err.println("propose: wrote ${pinFile.path}")
            }
        }
    }

    private fun writeProposals(dir: File, proposals: List<Proposal>) {
        dir.mkdirs()
        for (proposal in proposals) {
            val slot = dir.resolve(proposal.slot.id).apply { mkdirs() }
            PinFile.write(slot.resolve(PIN_FILE), proposal.pin)
            slot.resolve("branch").writeText("${proposal.branch}\n")
            slot.resolve("title").writeText("${proposal.title}\n")
        }
    }

    companion object {
        const val USAGE = "usage: propose --pin <file> [--feeds-url <url>] [--docs-url <url>] [--write] [--proposals-dir <dir>]"
        private const val PIN_FILE = "offered-gradle-versions.properties"
    }
}
