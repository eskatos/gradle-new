package gradlenew.adapterjson

import gradlenew.versions.FeedEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

object FeedJson {

    fun decode(text: String): FeedEntry? {
        val json = Documents.parse(text)
        if (json.isEmpty()) return null
        require(json["version"] != null) { "A versions feed entry has no 'version'" }
        return FeedEntry(
            version = json.string("version"),
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
