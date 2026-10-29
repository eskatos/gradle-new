package gradlenew.adapterjson

import gradlenew.domain.model.BuildType
import gradlenew.domain.model.Choice
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.Language
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object InitModelJson {

    fun encode(model: InitModel): String = Documents.format.encodeToString(JsonObject.serializer(), toJson(model))

    fun decode(text: String): InitModel = fromJson(Documents.parse(text))

    private fun toJson(model: InitModel): JsonObject = buildJsonObject {
        put("gradleVersion", model.gradleVersion)
        putJsonArray("componentTypes") { model.componentTypes.forEach { add(toJson(it)) } }
        putJsonArray("languages") { model.languages.forEach { add(named(it.id, it.displayName)) } }
        putJsonArray("dsls") { model.dsls.forEach { add(toJson(it)) } }
        putJsonArray("modularizationOptions") { model.modularizationOptions.forEach { add(named(it.id, it.displayName)) } }
        putJsonArray("testFrameworks") { model.testFrameworks.forEach { add(named(it.id, it.displayName)) } }
        putJsonArray("buildTypes") { model.buildTypes.forEach { add(toJson(it)) } }
        put(
            "javaVersion",
            buildJsonObject {
                put("minimum", model.javaVersion.minimum)
                put("default", model.javaVersion.default)
                put("maximum", model.javaVersion.maximum)
            },
        )
        put("packageName", toJson(model.packageName))
        put("projectName", toJson(model.projectName))
    }

    private fun named(id: String, displayName: String) = buildJsonObject { put("id", id); put("displayName", displayName) }

    private fun toJson(type: ComponentType) = buildJsonObject {
        put("id", type.id)
        put("displayName", type.displayName)
        putJsonArray("defaultProjectNames") { type.defaultProjectNames.forEach { add(JsonPrimitive(it)) } }
    }

    private fun toJson(dsl: Dsl) = buildJsonObject {
        put("id", dsl.id)
        put("displayName", dsl.displayName)
        put("fileExtension", dsl.fileExtension)
    }

    private fun toJson(type: BuildType) = buildJsonObject {
        put("id", type.id)
        put("componentType", type.componentType)
        put("language", type.language)
        put("dsls", toJson(type.dsls))
        put("modularization", toJson(type.modularization))
        putJsonArray("testFrameworks") { type.testFrameworks.forEach { add(toJson(it)) } }
        put("supportsPackage", type.supportsPackage)
        put("supportsJavaVersion", type.supportsJavaVersion)
        put("supportsProjectName", type.supportsProjectName)
        put("furtherReading", type.furtherReading)
    }

    private fun toJson(choice: Choice) = buildJsonObject {
        putJsonArray("values") { choice.values.forEach { add(JsonPrimitive(it)) } }
        put("default", choice.default)
    }

    private fun toJson(choice: TestFrameworkChoice) = buildJsonObject {
        put("modularization", choice.modularization)
        putJsonArray("values") { choice.values.forEach { add(JsonPrimitive(it)) } }
        put("default", choice.default)
    }

    private fun toJson(parameter: TextParameter) = buildJsonObject {
        put("pattern", parameter.pattern)
        put("default", parameter.default)
    }

    private fun fromJson(json: JsonObject): InitModel = InitModel(
        gradleVersion = json.string("gradleVersion"),
        componentTypes = json.array("componentTypes").map { it.jsonObject.let { o -> ComponentType(o.string("id"), o.string("displayName"), o.strings("defaultProjectNames")) } },
        languages = json.array("languages").map { it.jsonObject.let { o -> Language(o.string("id"), o.string("displayName")) } },
        dsls = json.array("dsls").map { it.jsonObject.let { o -> Dsl(o.string("id"), o.string("displayName"), o.string("fileExtension")) } },
        modularizationOptions = json.array("modularizationOptions").map { it.jsonObject.let { o -> ModularizationOption(o.string("id"), o.string("displayName")) } },
        testFrameworks = json.array("testFrameworks").map { it.jsonObject.let { o -> TestFramework(o.string("id"), o.string("displayName")) } },
        buildTypes = json.array("buildTypes").map { buildType(it.jsonObject) },
        javaVersion = json.obj("javaVersion").let { JavaVersionParameter(it.int("minimum"), it.int("default"), it.int("maximum")) },
        packageName = textParameter(json.obj("packageName")),
        projectName = textParameter(json.obj("projectName")),
    )

    private fun buildType(json: JsonObject) = BuildType(
        id = json.string("id"),
        componentType = json.string("componentType"),
        language = json.stringOrNull("language"),
        dsls = choice(json.obj("dsls")),
        modularization = choice(json.obj("modularization")),
        testFrameworks = json.array("testFrameworks").map { it.jsonObject.let { o -> TestFrameworkChoice(o.string("modularization"), o.strings("values"), o.stringOrNull("default")) } },
        supportsPackage = json.boolean("supportsPackage"),
        supportsJavaVersion = json.boolean("supportsJavaVersion"),
        supportsProjectName = json.boolean("supportsProjectName"),
        furtherReading = json.stringOrNull("furtherReading"),
    )

    private fun choice(json: JsonObject) = Choice(json.strings("values"), json.string("default"))

    private fun textParameter(json: JsonObject) = TextParameter(json.stringOrNull("pattern"), json.stringOrNull("default"))
}

internal object Documents {

    val format = kotlinx.serialization.json.Json { prettyPrint = true }

    fun parse(text: String): JsonObject =
        try {
            format.parseToJsonElement(text).jsonObject
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalArgumentException("Malformed JSON document: ${e.message}", e)
        }
}

internal fun JsonObject.element(name: String): JsonElement =
    requireNotNull(this[name]) { "Missing property '$name'" }

internal fun JsonObject.string(name: String): String =
    requireNotNull(stringOrNull(name)) { "Property '$name' must not be null" }

internal fun JsonObject.stringOrNull(name: String): String? =
    element(name).let { if (it is JsonNull) null else it.jsonPrimitive.content }

internal fun JsonObject.strings(name: String): List<String> = array(name).map { it.jsonPrimitive.content }

internal fun JsonObject.array(name: String): JsonArray = element(name).jsonArray

internal fun JsonObject.obj(name: String): JsonObject = element(name).jsonObject

internal fun JsonObject.int(name: String): Int = element(name).jsonPrimitive.int

internal fun JsonObject.boolean(name: String): Boolean = element(name).jsonPrimitive.boolean
