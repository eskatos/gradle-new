package gradlenew.publisher

import gradlenew.adapterfeeds.FeedUnavailable
import gradlenew.adapterfeeds.RawFeeds
import gradlenew.adapterfeeds.VersionFeeds
import gradlenew.adapterjson.FeedJson
import gradlenew.adapterjson.ProposalsJson
import gradlenew.versions.Feeds
import gradlenew.versions.OfferingPolicy
import gradlenew.versions.Proposals
import java.io.File
import java.io.PrintStream
import java.net.URI

class ProposeCommand(private val fetch: (URI) -> RawFeeds = { VersionFeeds(it).fetch() }) {

    fun run(arguments: List<String>, out: PrintStream, err: PrintStream): Int =
        try {
            propose(arguments, out, err)
            0
        } catch (failure: FeedUnavailable) {
            err.println("propose: ${failure.message}; the pin is unchanged")
            1
        } catch (failure: IllegalArgumentException) {
            err.println("propose: ${failure.message}")
            1
        }

    private fun propose(arguments: List<String>, out: PrintStream, err: PrintStream) {
        var pinPath: String? = null
        var feedsUrl = VersionFeeds.DEFAULT_BASE_URL
        var write = false
        var i = 0
        while (i < arguments.size) {
            when (val option = arguments[i]) {
                "--pin" -> pinPath = arguments.getOrNull(++i)
                "--feeds-url" -> feedsUrl = URI(requireNotNull(arguments.getOrNull(++i)) { "--feeds-url needs a value" })
                "--write" -> write = true
                else -> throw IllegalArgumentException("unknown option '$option'; usage: propose --pin <file> [--feeds-url <url>] [--write]")
            }
            i++
        }
        val pinFile = File(requireNotNull(pinPath) { "missing --pin <file>" })
        val pin = PinFile.read(pinFile)
        val raw = fetch(feedsUrl)
        val offered = OfferingPolicy.offer(Feeds(FeedJson.decode(raw.current), FeedJson.decode(raw.releaseCandidate)), pin)
        val proposals = Proposals.of(pin, offered)
        out.println(ProposalsJson.encode(proposals))
        if (proposals.isEmpty()) {
            err.println("propose: the pin matches the feeds, nothing to propose")
        } else {
            proposals.forEach { err.println("propose: ${it.title} (${it.branch})") }
            if (write) {
                PinFile.write(pinFile, offered)
                err.println("propose: wrote ${pinFile.path}")
            }
        }
    }
}
