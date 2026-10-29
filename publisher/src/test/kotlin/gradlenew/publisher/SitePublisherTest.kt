package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.generation.TemplateIndex
import gradlenew.domain.model.BuildType
import gradlenew.domain.model.Choice
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.Language
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.OfferedVersions
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import gradlenew.substitution.ProjectInputs
import gradlenew.verification.Check
import gradlenew.verification.InitGenerator
import gradlenew.verification.InitOutcome
import gradlenew.verification.Outcome
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SitePublisherTest {

    private val root = Files.createTempDirectory("publisher").toFile()
    private val output = root.resolve("site")
    private val templatesOutput = root.resolve("templates")
    private val reportOutput = root.resolve("report")
    private val workDir = root.resolve("work")

    private fun model(version: String) = InitModel(
        gradleVersion = version,
        componentTypes = listOf(ComponentType("basic", "Basic", emptyList()), ComponentType("library", "Library", listOf("lib"))),
        languages = listOf(Language("groovy", "Groovy")),
        dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts"), Dsl("groovy", "Groovy", ".gradle")),
        modularizationOptions = listOf(ModularizationOption("single-project", "Single application project")),
        testFrameworks = listOf(TestFramework("spock", "Spock")),
        buildTypes = listOf(
            BuildType(
                id = "basic", componentType = "basic", language = null,
                dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
                modularization = Choice(listOf("single-project"), "single-project"),
                testFrameworks = listOf(TestFrameworkChoice("single-project", emptyList(), null)),
                supportsPackage = false, supportsJavaVersion = false, supportsProjectName = true, furtherReading = null,
            ),
            BuildType(
                id = "groovy-library", componentType = "library", language = "groovy",
                dsls = Choice(listOf("groovy"), "groovy"),
                modularization = Choice(listOf("single-project"), "single-project"),
                testFrameworks = listOf(TestFrameworkChoice("single-project", listOf("spock"), "spock")),
                supportsPackage = true, supportsJavaVersion = true, supportsProjectName = true, furtherReading = null,
            ),
        ),
        javaVersion = JavaVersionParameter(7, 8, 9),
        packageName = TextParameter(null, "org.example"),
        projectName = TextParameter(null, null),
    )

    private fun files(inputs: ProjectInputs, extra: List<FileEntry> = emptyList()) = FileSet.of(
        buildList {
            add(FileEntry("settings.gradle.kts", "rootProject.name = \"${inputs.projectName}\"".encodeToByteArray(), false))
            add(FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true))
            inputs.packageName?.let { add(FileEntry("lib/src/main/groovy/${it.replace('.', '/')}/Library.groovy", "package $it".encodeToByteArray(), false)) }
            inputs.javaVersion?.let { add(FileEntry("lib/build.gradle", "languageVersion = JavaLanguageVersion.of($it)".encodeToByteArray(), false)) }
            addAll(extra)
        },
    )

    private val clean = InitGenerator { _, inputs -> InitOutcome.Generated(files(inputs)) }

    private fun nondeterministic(): InitGenerator {
        var run = 0
        return InitGenerator { _, inputs -> InitOutcome.Generated(files(inputs, listOf(FileEntry("generated-at.txt", "${run++}".encodeToByteArray(), false)))) }
    }

    private fun publisher(
        initFor: (String) -> InitGenerator = { clean },
        modelFor: (String) -> InitModel = ::model,
        budgetBytes: Long = Long.MAX_VALUE,
    ) = SitePublisher(modelFor, initFor, budgetBytes)

    private fun publish(publisher: SitePublisher, versions: OfferedVersions) =
        publisher.publish(versions, output, templatesOutput, reportOutput, workDir)

    @Test
    fun publishesEveryVersionThatPassesAndExitsZero() {
        val result = publish(publisher(), OfferedVersions("9.8.2", "9.9.0-rc-1"))

        assertEquals(0, result.exitCode)
        assertEquals(OfferedVersions("9.8.2", "9.9.0-rc-1"), VersionsJson.decode(output.resolve("versions.json").readText()))
        assertEquals(model("9.9.0-rc-1"), InitModelJson.decode(output.resolve("v/9.9.0-rc-1/model.json").readText()))
        val keys = TemplateCatalog.of(model("9.8.2")).map { "${it.spec.key}.zip" }.toSet()
        assertEquals(keys + "index.json", templatesOutput.resolve("9.8.2").list()!!.toSet())
        assertEquals(TemplateIndex.NONE, TemplateIndexJson.decode(templatesOutput.resolve("9.8.2/index.json").readText()))
        val key = "basic.groovy.none.single-project.no-incubating.comments"
        assertEquals(files(TemplateCatalog.of(model("9.8.2")).first { it.spec.key == key }.placeholders), Zip.read(templatesOutput.resolve("9.8.2/$key.zip").readBytes()))
        assertTrue(output.walkTopDown().none { it.extension == "zip" })
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText())
        assertEquals(listOf("9.8.2", "9.9.0-rc-1"), report.passedVersions)
        assertTrue(result.summary.contains("Gradle 9.8.2: passed"), result.summary)
    }

    @Test
    fun publishesTheStableAloneAndExitsTwoWhenOnlyTheRcFails() {
        val result = publish(publisher(initFor = { version -> if (version == "9.9.0-rc-1") nondeterministic() else clean }), OfferedVersions("9.8.2", "9.9.0-rc-1"))

        assertEquals(2, result.exitCode)
        assertEquals(OfferedVersions("9.8.2", null), VersionsJson.decode(output.resolve("versions.json").readText()))
        assertEquals(listOf("9.8.2"), output.resolve("v").list()!!.toList())
        assertEquals(listOf("9.8.2"), templatesOutput.list()!!.toList())
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText())
        assertEquals(Outcome.FAILED, report.version("9.9.0-rc-1").check(Check.DETERMINISM)!!.outcome)
    }

    @Test
    fun publishesNothingAndExitsOneWhenTheStableFails() {
        output.resolve("v/old").mkdirs()
        output.resolve("versions.json").writeText("stale")
        templatesOutput.resolve("old").mkdirs()

        val result = publish(publisher(initFor = { version -> if (version == "9.8.2") nondeterministic() else clean }), OfferedVersions("9.8.2", "9.9.0-rc-1"))

        assertEquals(1, result.exitCode)
        assertFalse(output.walkTopDown().any { it.isFile || it.name == "old" }, output.walkTopDown().toList().toString())
        assertFalse(templatesOutput.walkTopDown().any { it.isFile || it.name == "old" })
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText())
        assertEquals(listOf("9.9.0-rc-1"), report.passedVersions)
        assertTrue(result.summary.contains("Gradle 9.8.2: FAILED"), result.summary)
    }

    @Test
    fun reportsAModelThatCouldNotBeObtainedUnderTheModelCheck() {
        val result = publish(publisher(modelFor = { error("builder-plugin did not compile") }), OfferedVersions("9.8.2", null))

        assertEquals(1, result.exitCode)
        val model = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.2").check(Check.MODEL)!!
        assertEquals("the model could not be obtained: builder-plugin did not compile", model.failures.single().message)
    }

    @Test
    fun refusesAModelForAnotherVersion() {
        val result = publish(publisher(modelFor = { model("9.8.1") }), OfferedVersions("9.8.2", null))

        assertEquals(1, result.exitCode)
        val model = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.2").check(Check.MODEL)!!
        assertEquals("the model could not be obtained: the model reports Gradle 9.8.1, expected 9.8.2", model.failures.single().message)
    }

    @Test
    fun writesTheEnumeratedTypesIntoTheTemplateIndex() {
        val javaInComment = InitGenerator { spec, inputs ->
            val comment = if (spec.type == "groovy-library") listOf(FileEntry("lib/NOTES.txt", "Java ${inputs.javaVersion}".encodeToByteArray(), false)) else emptyList()
            InitOutcome.Generated(files(inputs, comment))
        }

        val result = publish(publisher(initFor = { javaInComment }), OfferedVersions("9.8.2", null))

        assertEquals(0, result.exitCode, result.summary)
        assertEquals(TemplateIndex(setOf("groovy-library")), TemplateIndexJson.decode(templatesOutput.resolve("9.8.2/index.json").readText()))
        val keys = templatesOutput.resolve("9.8.2").list()!!.filter { it.startsWith("groovy-library.") }
        assertEquals(4 * (7..9).count(), keys.size)
        assertEquals(setOf("java7", "java8", "java9"), keys.map { it.removeSuffix(".zip").substringAfterLast('.') }.toSet())
    }

    @Test
    fun failsTheVersionOverTheBudget() {
        val result = publish(publisher(budgetBytes = 10), OfferedVersions("9.8.2", null))

        assertEquals(1, result.exitCode)
        assertFalse(templatesOutput.walkTopDown().any { it.isFile })
    }

    @Test
    fun leavesNoStagingBehind() {
        publish(publisher(), OfferedVersions("9.8.2", null))

        assertFalse(workDir.resolve("staging").exists())
    }
}
