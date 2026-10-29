package gradlenew.adapterjson

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ErrorJson {

    fun encode(message: String): String =
        Documents.format.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", message) })

    fun decode(text: String): String = Documents.parse(text).string("error")
}
