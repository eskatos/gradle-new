package gradlenew.adapterjson

import gradlenew.verification.Check
import gradlenew.verification.CheckResult
import gradlenew.verification.ChecksumSource
import gradlenew.verification.Failure
import gradlenew.verification.Outcome
import gradlenew.verification.VerificationReport
import gradlenew.verification.VersionReport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object VerificationReportJson {

    fun encode(report: VerificationReport): String = Documents.format.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("passed", report.passed)
            putJsonArray("versions") { report.versions.forEach { add(toJson(it)) } }
        },
    )

    fun decode(text: String): VerificationReport =
        VerificationReport(Documents.parse(text).array("versions").map { version(it.jsonObject) })

    private fun toJson(version: VersionReport) = buildJsonObject {
        put("version", version.version)
        put("passed", version.passed)
        put("checksums", version.checksums.id)
        putJsonArray("javaEnumeratedTypes") { version.javaEnumeratedTypes.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("checks") { version.checks.forEach { add(toJson(it)) } }
    }

    private fun toJson(result: CheckResult) = buildJsonObject {
        put("check", result.check.id)
        put("outcome", result.outcome.name.lowercase())
        put("checked", result.checked)
        putJsonArray("failures") {
            result.failures.forEach { add(buildJsonObject { put("subject", it.subject); put("message", it.message) }) }
        }
    }

    private fun version(json: JsonObject) = VersionReport(
        version = json.string("version"),
        checks = json.array("checks").map { check(it.jsonObject) },
        javaEnumeratedTypes = json.strings("javaEnumeratedTypes"),
        checksums = json.stringOrNull("checksums")?.let(ChecksumSource::of) ?: ChecksumSource.PINNED,
    )

    private fun check(json: JsonObject) = CheckResult(
        check = Check.of(json.string("check")),
        outcome = outcome(json.string("outcome")),
        checked = json.int("checked"),
        failures = json.array("failures").map { it.jsonObject.let { o -> Failure(o.string("subject"), o.string("message")) } },
    )

    private fun outcome(value: String): Outcome =
        requireNotNull(Outcome.entries.firstOrNull { it.name.lowercase() == value }) { "Unknown outcome '$value'" }
}
