package gradlenew.adapterjson

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ErrorJson {

    fun encode(message: String, parameter: String? = null): String =
        Documents.format.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", message); put("parameter", parameter) })

    fun decode(text: String): String = Documents.parse(text).string("error")

    fun parameter(text: String): String? = Documents.parse(text)["parameter"]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.content }
}
