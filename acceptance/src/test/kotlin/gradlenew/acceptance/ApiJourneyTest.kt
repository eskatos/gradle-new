package gradlenew.acceptance

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import java.net.URLEncoder
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiJourneyTest {

    private val pinnedStable = System.getProperty("gradlenew.offered.stable") ?: error("gradlenew.offered.stable is not set")
    private val pinnedRc: String? = System.getProperty("gradlenew.offered.rc")

    @Test
    fun apiJourney() {
        Target.open().use { target ->
            val offered = readOfferedVersions(target)
            val model = readModel(target, offered.stable)
            val oracle = GradleOracle(offered.stable)
            compareWithGradle(model, oracle)
            generateBuilds(target, model, oracle)
            rejectInvalidRequest(target, model, oracle)
            offered.rc?.let { rc -> generateApplication(target, readModel(target, rc), GradleOracle(rc), "rc") }
            templatesAreNotServed(target, model)
            target.hasShell()?.let { assertEquals(false, it, "the image has no shell") }
        }
    }

    private fun readOfferedVersions(target: Target): OfferedVersions {
        val response = target.get("/api/versions")
        assertEquals(200, response.statusCode())
        assertJson(response)
        val versions = VersionsJson.decode(response.body())
        assertEquals(pinnedStable, versions.stable)
        assertEquals(pinnedRc, versions.rc)
        return versions
    }

    private fun readModel(target: Target, version: String): InitModel {
        val response = target.get("/v/$version/model.json")
        assertEquals(200, response.statusCode())
        assertJson(response)
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("immutable"))
        val model = InitModelJson.decode(response.body())
        assertEquals(version, model.gradleVersion)
        return model
    }

    private fun compareWithGradle(model: InitModel, oracle: GradleOracle) {
        val optionValues = oracle.initOptionValues()
        val generatorTypes = optionValues.getValue("type") - "pom"
        assertEquals(generatorTypes, model.buildTypes.map { it.id }.toSet())
        assertEquals(optionValues.getValue("dsl"), model.dsls.map { it.id }.toSet())
        assertEquals(optionValues.getValue("test-framework"), model.testFrameworks.map { it.id }.toSet())

        val withoutPackage = model.buildTypes.first { !it.supportsPackage }
        val withPackage = model.buildTypes.first { it.supportsPackage }
        assertTrue(oracle.initRejection(listOf("--type", withoutPackage.id, "--package", "org.example")) != null, withoutPackage.id)
        assertEquals(null, oracle.initRejection(listOf("--type", withPackage.id, "--package", "org.example")))
    }

    private fun generateBuilds(target: Target, model: InitModel, oracle: GradleOracle) {
        generateApplication(target, model, oracle, "stable")
        val plugin = model.buildTypes.first { it.componentType == "gradle-plugin" && it.supportsPackage }
        generateAndCompare(
            target, oracle, model.gradleVersion, "my-cool-thing",
            listOf(
                "type" to plugin.id,
                "dsl" to plugin.dsls.values.last(),
                "test-framework" to plugin.testFrameworksFor(plugin.modularization.default).default!!,
                "incubating" to "true",
                "comments" to "false",
                "project-name" to "my-cool-thing",
                "package" to "com.acme.tools",
                "java-version" to "17",
            ),
        )
    }

    private fun generateApplication(target: Target, model: InitModel, oracle: GradleOracle, version: String) {
        val application = model.buildTypes.first { it.componentType == "application" && it.supportsPackage && it.supportsJavaVersion }
        val modularization = application.modularization.default
        generateAndCompare(
            target, oracle, version, "demo-app",
            listOf(
                "type" to application.id,
                "dsl" to application.dsls.values.first(),
                "test-framework" to application.testFrameworksFor(modularization).values.last(),
                "split-project" to "false",
                "incubating" to "false",
                "comments" to "true",
                "project-name" to "demo-app",
                "package" to "com.acme.demo",
                "java-version" to "17",
            ),
        )
    }

    private fun generateAndCompare(target: Target, oracle: GradleOracle, version: String, projectName: String, options: List<Pair<String, String>>) {
        val path = "/api/generate?" + (listOf("version" to version) + options).joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        val response = target.getBytes(path)
        assertEquals(200, response.statusCode(), String(response.body()))
        assertEquals("application/zip", response.headers().firstValue("content-type").orElse(""))
        assertEquals("attachment; filename=\"$projectName.zip\"", response.headers().firstValue("content-disposition").orElse(""))
        assertTrue(response.headers().firstValue("etag").isPresent)

        val archive = GeneratedBuild.fromZip(response.body())
        assertTrue(archive.keys.all { it.startsWith("$projectName/") }, archive.keys.toString())
        val generated = archive.mapKeys { it.key.removePrefix("$projectName/") }
        val expected = oracle.init(options)
        assertEquals(emptyList(), GeneratedBuild.differences(expected, generated), "generated build differs from gradle init for $options")
        assertTrue(generated.getValue("gradlew").executable)

        val again = target.getBytes(path)
        assertTrue(response.body().contentEquals(again.body()), "two identical requests returned different archives")
    }

    private fun rejectInvalidRequest(target: Target, model: InitModel, oracle: GradleOracle) {
        val type = model.buildTypes.first { !it.supportsPackage }
        val initMessage = oracle.initRejection(listOf("--type", type.id, "--package", "org.example"))!!.lines().first()
        val response = target.get("/api/generate?type=${type.id}&package=org.example&project-name=demo")
        assertEquals(400, response.statusCode())
        assertJson(response)
        assertTrue(response.body().contains(initMessage), "${response.body()}\n--- init said: $initMessage")
    }

    private fun templatesAreNotServed(target: Target, model: InitModel) {
        val key = TemplateCatalog.of(model).first().spec.key
        assertEquals(404, target.get("/v/${model.gradleVersion}/templates/$key.zip").statusCode())
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun assertJson(response: HttpResponse<String>) {
        assertEquals("application/json", response.headers().firstValue("content-type").orElse("").substringBefore(';'))
    }
}
