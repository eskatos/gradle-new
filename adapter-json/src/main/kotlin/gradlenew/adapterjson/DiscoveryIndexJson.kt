package gradlenew.adapterjson

import gradlenew.domain.agent.DiscoveryIndex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object DiscoveryIndexJson {

    fun encode(index: DiscoveryIndex): String = Documents.format.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("\$schema", DiscoveryIndex.SCHEMA)
            putJsonArray("skills") {
                for (skill in index.skills) {
                    addJsonObject {
                        put("name", skill.name)
                        put("type", skill.type)
                        put("description", skill.description)
                        put("url", skill.url)
                        put("digest", skill.digest)
                    }
                }
            }
        },
    )
}
