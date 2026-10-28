package gradlenew.acceptance

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.InitModel
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiJourneyTest {

    private val pinnedStable = System.getProperty("acceptance.offered.stable") ?: error("acceptance.offered.stable is not set")
    private val pinnedRc: String? = System.getProperty("acceptance.offered.rc")

    @Test
    fun apiJourney() {
        Target.open().use { target ->
            val stable = readOfferedVersions(target)
            val model = readModel(target, stable)
            compareWithGradle(model, stable)
        }
    }

    private fun readOfferedVersions(target: Target): String {
        val response = target.get("/api/versions")
        assertEquals(200, response.statusCode())
        assertJson(response)
        val versions = VersionsJson.decode(response.body())
        assertEquals(pinnedStable, versions.stable)
        assertEquals(pinnedRc, versions.rc)
        return versions.stable
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

    private fun compareWithGradle(model: InitModel, version: String) {
        val oracle = GradleOracle(version)
        val optionValues = oracle.initOptionValues()
        val generatorTypes = optionValues.getValue("type") - "pom"
        assertEquals(generatorTypes, model.buildTypes.map { it.id }.toSet())
        assertEquals(optionValues.getValue("dsl"), model.dsls.map { it.id }.toSet())
        assertEquals(optionValues.getValue("test-framework"), model.testFrameworks.map { it.id }.toSet())

        val cpp = model.buildType("cpp-application")
        val java = model.buildType("java-application")
        assertFalse(cpp.supportsPackage)
        assertTrue(java.supportsPackage)
        assertFalse(oracle.initAccepts(listOf("--type", "cpp-application", "--package", "org.example")))
        assertTrue(oracle.initAccepts(listOf("--type", "java-application", "--package", "org.example")))
    }

    private fun assertJson(response: HttpResponse<String>) {
        assertEquals("application/json", response.headers().firstValue("content-type").orElse("").substringBefore(';'))
    }
}
