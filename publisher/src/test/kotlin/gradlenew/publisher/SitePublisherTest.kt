package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.model.BuildType
import gradlenew.domain.model.Choice
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.OfferedVersions
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SitePublisherTest {

    private val output = Files.createTempDirectory("site").toFile()
    private val generated = mutableListOf<Pair<String, String>>()

    private fun model(version: String) = InitModel(
        gradleVersion = version,
        componentTypes = listOf(ComponentType("basic", "Basic", emptyList())),
        languages = emptyList(),
        dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts"), Dsl("groovy", "Groovy", ".gradle")),
        modularizationOptions = listOf(ModularizationOption("single-project", "Single application project")),
        testFrameworks = emptyList(),
        buildTypes = listOf(
            BuildType(
                id = "basic",
                componentType = "basic",
                language = null,
                dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
                modularization = Choice(listOf("single-project"), "single-project"),
                testFrameworks = listOf(TestFrameworkChoice("single-project", emptyList(), null)),
                supportsPackage = false,
                supportsJavaVersion = false,
                supportsProjectName = true,
                furtherReading = null,
            ),
        ),
        javaVersion = JavaVersionParameter(7, 21),
        packageName = TextParameter(null, "org.example"),
        projectName = TextParameter(null, null),
    )

    private fun template(version: String, entry: CatalogEntry): FileSet {
        generated += version to entry.spec.key
        return FileSet.of(
            FileEntry("settings.gradle.kts", "rootProject.name = \"${entry.placeholders.projectName}\" // ${entry.spec.key}".encodeToByteArray(), false),
            FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true),
        )
    }

    private val publisher = SitePublisher(::model, ::template)

    @Test
    fun writesTheVersionsIndexAndOneModelPerOfferedVersion() {
        publisher.publish(OfferedVersions("9.8.2", "9.9.0-rc-1"), output)

        assertEquals(OfferedVersions("9.8.2", "9.9.0-rc-1"), VersionsJson.decode(output.resolve("versions.json").readText()))
        assertEquals(model("9.8.2"), InitModelJson.decode(output.resolve("v/9.8.2/model.json").readText()))
        assertEquals(model("9.9.0-rc-1"), InitModelJson.decode(output.resolve("v/9.9.0-rc-1/model.json").readText()))
    }

    @Test
    fun writesOneTemplateZipPerCatalogEntryAndVersion() {
        publisher.publish(OfferedVersions("9.8.2", null), output)

        val templates = output.resolve("v/9.8.2/templates").listFiles()!!.map { it.name }.sorted()
        assertEquals(8, templates.size)
        assertEquals(generated.map { "${it.second}.zip" }.sorted(), templates)
        assertTrue(generated.all { it.first == "9.8.2" })
        val key = "basic.groovy.none.single-project.incubating.no-comments"
        val files = Zip.read(output.resolve("v/9.8.2/templates/$key.zip").readBytes())
        assertEquals(template("9.8.2", entryFor(key)), files)
    }

    @Test
    fun writesIdenticalTemplatesOnEveryRun() {
        publisher.publish(OfferedVersions("9.8.2", null), output)
        val first = output.resolve("v/9.8.2/templates").listFiles()!!.associate { it.name to it.readBytes() }
        publisher.publish(OfferedVersions("9.8.2", null), output)
        val second = output.resolve("v/9.8.2/templates").listFiles()!!.associate { it.name to it.readBytes() }

        assertEquals(first.keys, second.keys)
        assertTrue(first.all { (name, bytes) -> bytes.contentEquals(second.getValue(name)) })
    }

    @Test
    fun replacesStaleContentAndSkipsTheRcWhenAbsent() {
        output.resolve("v/old").mkdirs()
        publisher.publish(OfferedVersions("9.8.2", null), output)

        assertFalse(output.resolve("v/old").exists())
        assertFalse(output.resolve("v").listFiles()!!.any { it.name != "9.8.2" })
    }

    @Test
    fun refusesAModelForAnotherVersion() {
        assertFailsWith<IllegalArgumentException> {
            SitePublisher({ model("9.8.1") }, ::template).publish(OfferedVersions("9.8.2", null), output)
        }
    }

    private fun entryFor(key: String) =
        gradlenew.domain.generation.TemplateCatalog.of(model("9.8.2")).first { it.spec.key == key }
}
