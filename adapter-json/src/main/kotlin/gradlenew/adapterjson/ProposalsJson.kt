package gradlenew.adapterjson

import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
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
                Pin(pin.string("stable"), if (pin["rc"] == null || pin["rc"] is JsonNull) null else pin.string("rc")),
            )
        }
}
