package gradlenew.adapterjson

import gradlenew.versions.FeedEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

object FeedJson {

    fun decode(text: String): List<FeedEntry> {
        val entries = try {
            Documents.format.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Malformed versions feed: ${e.message}", e)
        }
        require(entries is JsonArray) { "The versions feed is not a list of entries" }
        return entries.map { element ->
            require(element is JsonObject) { "A versions feed entry is not an object: $element" }
            entry(element)
        }
    }

    private fun entry(json: JsonObject): FeedEntry {
        require(json["version"] != null) { "A versions feed entry has no 'version'" }
        return FeedEntry(
            version = json.string("version"),
            current = json.flag("current"),
            activeRc = json.flag("activeRc"),
            rcFor = json.text("rcFor"),
            milestoneFor = json.text("milestoneFor"),
            broken = json.flag("broken"),
            snapshot = json.flag("snapshot"),
            nightly = json.flag("nightly"),
            releaseNightly = json.flag("releaseNightly"),
            checksum = json.text("checksum"),
            wrapperChecksum = json.text("wrapperChecksum"),
        )
    }

    private fun JsonObject.flag(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.text(name: String): String = if (this[name] == null) "" else stringOrNull(name) ?: ""
}
