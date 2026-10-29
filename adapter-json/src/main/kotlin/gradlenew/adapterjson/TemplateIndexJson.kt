package gradlenew.adapterjson

import gradlenew.domain.generation.TemplateIndex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

object TemplateIndexJson {

    fun encode(index: TemplateIndex): String = Documents.format.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { putJsonArray("javaEnumeratedTypes") { index.javaEnumeratedTypes.sorted().forEach { add(JsonPrimitive(it)) } } },
    )

    fun decode(text: String): TemplateIndex = TemplateIndex(Documents.parse(text).strings("javaEnumeratedTypes").toSet())
}
