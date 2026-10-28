package gradlenew.adapterjson

import gradlenew.domain.model.OfferedVersions
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object VersionsJson {

    fun encode(versions: OfferedVersions): String = Documents.format.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("stable", versions.stable)
            put("rc", versions.rc)
        },
    )

    fun decode(text: String): OfferedVersions {
        val json = Documents.parse(text)
        return OfferedVersions(json.string("stable"), if (json["rc"] == null || json["rc"] is JsonNull) null else json.string("rc"))
    }
}
