package gradlenew.adapterjson

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
                            put("stable", proposal.pin.stable)
                            put("rc", proposal.pin.rc)
                            putJsonObject("checksums") {
                                put("stable", checksums(proposal.pin.stableChecksums))
                                put("rc", checksums(proposal.pin.rcChecksums))
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
            val checksums = pin["checksums"]?.takeUnless { it is JsonNull }?.jsonObject
            Proposal(
                Slot.of(proposal.string("slot")),
                proposal.string("title"),
                Pin(
                    pin.string("stable"),
                    if (pin["rc"] == null || pin["rc"] is JsonNull) null else pin.string("rc"),
                    checksums(checksums?.get("stable")),
                    checksums(checksums?.get("rc")),
                ),
            )
        }

    private fun checksums(checksums: Checksums?): JsonElement =
        if (checksums == null) JsonNull else buildJsonObject {
            put("distribution", checksums.distribution)
            put("wrapper", checksums.wrapper)
        }

    private fun checksums(element: JsonElement?): Checksums? =
        element?.takeUnless { it is JsonNull }?.jsonObject?.let { Checksums(it.string("distribution"), it.string("wrapper")) }
}
