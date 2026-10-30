package gradlenew.adapterjson

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.PinnedVersion
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ProposalsJson {

    fun encode(proposals: List<Proposal>): String = Documents.format.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            putJsonArray("proposals") {
                for (proposal in proposals) {
                    addJsonObject {
                        put("slot", proposal.slot.id)
                        put("branch", proposal.branch)
                        put("title", proposal.title)
                        putJsonObject("pin") {
                            for (slot in Slot.entries) {
                                put(slot.id, pinned(proposal.pin[slot]))
                            }
                        }
                    }
                }
            }
        },
    )

    fun decode(text: String): List<Proposal> =
        Documents.parse(text).array("proposals").map { element ->
            val proposal = element.jsonObject
            val pin = proposal.obj("pin")
            Proposal(
                Slot.of(proposal.string("slot")),
                proposal.string("title"),
                Pin(
                    requireNotNull(pinned(pin["stable"])) { "A proposal pins no stable version" },
                    requireNotNull(pinned(pin["previous"])) { "A proposal pins no previous major" },
                    pinned(pin["rc"]),
                ),
            )
        }

    private fun pinned(pinned: PinnedVersion?): JsonElement =
        if (pinned == null) JsonNull else buildJsonObject {
            put("version", pinned.version)
            put("java", pinned.java)
            put("checksums", checksums(pinned.checksums))
        }

    private fun pinned(element: JsonElement?): PinnedVersion? =
        element?.takeUnless { it is JsonNull }?.jsonObject?.let {
            PinnedVersion(it.string("version"), it.element("java").jsonPrimitive.int, checksums(it["checksums"]))
        }

    private fun checksums(checksums: Checksums?): JsonElement =
        if (checksums == null) JsonNull else buildJsonObject {
            put("distribution", checksums.distribution)
            put("wrapper", checksums.wrapper)
        }

    private fun checksums(element: JsonElement?): Checksums? =
        element?.takeUnless { it is JsonNull }?.jsonObject?.let { Checksums(it.string("distribution"), it.string("wrapper")) }
}
