package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.OfferedVersions
import gradlenew.domain.model.TextParameter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SitePublisherTest {

    private val output = Files.createTempDirectory("site").toFile()

    private fun model(version: String) = InitModel(
        gradleVersion = version,
        componentTypes = listOf(ComponentType("basic", "Basic", emptyList())),
        languages = emptyList(),
        dsls = emptyList(),
        modularizationOptions = emptyList(),
        testFrameworks = emptyList(),
        buildTypes = emptyList(),
        javaVersion = JavaVersionParameter(7, 21),
        packageName = TextParameter(null, "org.example"),
        projectName = TextParameter(null, null),
    )

    @Test
    fun writesTheVersionsIndexAndOneModelPerOfferedVersion() {
        SitePublisher(::model).publish(OfferedVersions("9.8.2", "9.9.0-rc-1"), output)

        assertEquals(OfferedVersions("9.8.2", "9.9.0-rc-1"), VersionsJson.decode(output.resolve("versions.json").readText()))
        assertEquals(model("9.8.2"), InitModelJson.decode(output.resolve("v/9.8.2/model.json").readText()))
        assertEquals(model("9.9.0-rc-1"), InitModelJson.decode(output.resolve("v/9.9.0-rc-1/model.json").readText()))
    }

    @Test
    fun replacesStaleContentAndSkipsTheRcWhenAbsent() {
        output.resolve("v/old").mkdirs()
        SitePublisher(::model).publish(OfferedVersions("9.8.2", null), output)

        assertFalse(output.resolve("v/old").exists())
        assertFalse(output.resolve("v").listFiles()!!.any { it.name != "9.8.2" })
    }

    @Test
    fun refusesAModelForAnotherVersion() {
        assertFailsWith<IllegalArgumentException> {
            SitePublisher { model("9.8.1") }.publish(OfferedVersions("9.8.2", null), output)
        }
    }
}
