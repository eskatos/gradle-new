package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
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
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import gradlenew.substitution.ProjectInputs
import gradlenew.versions.Checksums
import gradlenew.verification.Check
import gradlenew.verification.ChecksumSource
import gradlenew.verification.DistributionCheck
import gradlenew.verification.InitGenerator
import gradlenew.verification.InitOutcome
import gradlenew.verification.Outcome
import java.net.URI
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
            add(FileEntry("gradle/wrapper/gradle-wrapper.jar", wrapperJar, false))
            inputs.packageName?.let { add(FileEntry("lib/src/main/groovy/${it.replace('.', '/')}/Library.groovy", "package $it".encodeToByteArray(), false)) }
            inputs.javaVersion?.let { add(FileEntry("lib/build.gradle", "languageVersion = JavaLanguageVersion.of($it)".encodeToByteArray(), false)) }
            addAll(extra)
        },
    )

    private val wrapperJar = "the wrapper".encodeToByteArray()

    private fun verified(version: String, source: ChecksumSource = ChecksumSource.PINNED) =
        DistributionOutcome.Verified(VerifiedDistribution(version, URI("file:/distributions/gradle-$version-bin.zip"), Checksums("a".repeat(64), Sha256.hex(wrapperJar)), source))

    private val clean = InitGenerator { _, inputs -> InitOutcome.Generated(files(inputs)) }

    private fun nondeterministic(): InitGenerator {
        var run = 0
        return InitGenerator { _, inputs -> InitOutcome.Generated(files(inputs, listOf(FileEntry("generated-at.txt", "${run++}".encodeToByteArray(), false)))) }
    }

    private fun publisher(
        initFor: (VerifiedDistribution) -> InitGenerator = { clean },
        modelFor: (VerifiedDistribution) -> InitModel = { model(it.version) },
        budgetBytes: Long = Long.MAX_VALUE,
        distributionFor: (String) -> DistributionOutcome = { verified(it) },
    ) = SitePublisher(distributionFor, modelFor, initFor, budgetBytes)

    private fun publish(publisher: SitePublisher, version: String) =
        publisher.publish(version, output, templatesOutput, reportOutput, workDir)

    @Test
    fun publishesAVersionThatPassesAndExitsZero() {
        val result = publish(publisher(), "9.9.0-rc-1")

        assertEquals(0, result.exitCode)
        assertEquals(model("9.9.0-rc-1"), InitModelJson.decode(output.resolve("v/9.9.0-rc-1/model.json").readText()))
        assertEquals(listOf("v"), output.list()!!.toList())
        val keys = TemplateCatalog.of(model("9.9.0-rc-1")).map { "${it.spec.key}.zip" }.toSet()
        assertEquals(keys + "index.json", templatesOutput.resolve("9.9.0-rc-1").list()!!.toSet())
        assertEquals(TemplateIndex.NONE, TemplateIndexJson.decode(templatesOutput.resolve("9.9.0-rc-1/index.json").readText()))
        val key = "basic.groovy.none.single-project.no-incubating.comments"
        assertEquals(files(TemplateCatalog.of(model("9.9.0-rc-1")).first { it.spec.key == key }.placeholders), Zip.read(templatesOutput.resolve("9.9.0-rc-1/$key.zip").readBytes()))
        assertTrue(output.walkTopDown().none { it.extension == "zip" })
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText())
        assertEquals(listOf("9.9.0-rc-1"), report.passedVersions)
        assertEquals(Check.entries, report.version("9.9.0-rc-1").checks.map { it.check }, "the distribution check comes first")
        assertEquals(ChecksumSource.PINNED, report.version("9.9.0-rc-1").checksums)
        assertTrue(result.summary.contains("Gradle 9.9.0-rc-1: passed"), result.summary)
    }

    @Test
    fun publishesNothingAndExitsOneWhenTheVersionFails() {
        output.resolve("v/old").mkdirs()
        templatesOutput.resolve("old").mkdirs()

        val result = publish(publisher(initFor = { nondeterministic() }), "9.8.2")

        assertEquals(1, result.exitCode)
        assertFalse(output.walkTopDown().any { it.isFile || it.name == "old" }, output.walkTopDown().toList().toString())
        assertFalse(templatesOutput.walkTopDown().any { it.isFile || it.name == "old" })
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText())
        assertEquals(Outcome.FAILED, report.version("9.8.2").check(Check.DETERMINISM)!!.outcome)
        assertTrue(result.summary.contains("Gradle 9.8.2: FAILED"), result.summary)
    }

    @Test
    fun reportsAModelThatCouldNotBeObtainedUnderTheModelCheck() {
        val result = publish(publisher(modelFor = { error("builder-plugin did not compile") }), "9.8.2")

        assertEquals(1, result.exitCode)
        val model = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.2").check(Check.MODEL)!!
        assertEquals("the model could not be obtained: builder-plugin did not compile", model.failures.single().message)
    }

    @Test
    fun refusesAModelForAnotherVersion() {
        val result = publish(publisher(modelFor = { model("9.8.1") }), "9.8.2")

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

        val result = publish(publisher(initFor = { javaInComment }), "9.8.2")

        assertEquals(0, result.exitCode, result.summary)
        assertEquals(TemplateIndex(setOf("groovy-library")), TemplateIndexJson.decode(templatesOutput.resolve("9.8.2/index.json").readText()))
        val keys = templatesOutput.resolve("9.8.2").list()!!.filter { it.startsWith("groovy-library.") }
        assertEquals(4 * (7..9).count(), keys.size)
        assertEquals(setOf("java7", "java8", "java9"), keys.map { it.removeSuffix(".zip").substringAfterLast('.') }.toSet())
    }

    @Test
    fun failsTheVersionOverTheBudget() {
        val result = publish(publisher(budgetBytes = 10), "9.8.2")

        assertEquals(1, result.exitCode)
        assertFalse(templatesOutput.walkTopDown().any { it.isFile })
    }

    @Test
    fun leavesNoStagingBehind() {
        publish(publisher(), "9.8.2")

        assertFalse(workDir.resolve("staging").exists())
    }

    @Test
    fun aDistributionWithAnotherChecksumPublishesNothing() {
        var modelRequested = false
        val result = publish(
            publisher(
                distributionFor = { DistributionOutcome.Rejected(DistributionCheck.of(it, "a".repeat(64), "b".repeat(64))) },
                modelFor = { modelRequested = true; model(it.version) },
            ),
            "9.8.2",
        )

        assertEquals(1, result.exitCode)
        assertFalse(modelRequested, "nothing runs a distribution that failed its checksum")
        assertFalse(output.walkTopDown().any { it.isFile })
        assertFalse(templatesOutput.walkTopDown().any { it.isFile })
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.2")
        assertEquals(Outcome.FAILED, report.check(Check.DISTRIBUTION)!!.outcome)
        assertTrue(report.checks.drop(1).all { it.outcome == Outcome.SKIPPED }, report.toString())
        assertTrue(result.summary.contains("SHA-256 is ${"b".repeat(64)}, expected ${"a".repeat(64)}"), result.summary)
    }

    @Test
    fun aWrapperJarThatIsNotThePinnedOneFailsTheVersion() {
        val result = publish(publisher(distributionFor = { DistributionOutcome.Verified(VerifiedDistribution(it, URI("file:/x.zip"), Checksums("a".repeat(64), "c".repeat(64)), ChecksumSource.PINNED)) }), "9.8.2")

        assertEquals(1, result.exitCode)
        val report = VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.2")
        assertEquals(Outcome.FAILED, report.check(Check.WRAPPER)!!.outcome)
        assertFalse(templatesOutput.walkTopDown().any { it.isFile })
    }

    @Test
    fun recordsThatTheChecksumsWereNotPinned() {
        val result = publish(publisher(distributionFor = { verified(it, ChecksumSource.PUBLISHED) }), "9.8.0-rc-3")

        assertEquals(0, result.exitCode, result.summary)
        assertEquals(ChecksumSource.PUBLISHED, VerificationReportJson.decode(reportOutput.resolve("verification-report.json").readText()).version("9.8.0-rc-3").checksums)
        assertTrue(result.summary.contains("not pinned"), result.summary)
    }
}
