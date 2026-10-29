package gradlenew.acceptance

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.InitModel
import gradlenew.verification.Check
import gradlenew.verification.Outcome
import gradlenew.verification.VersionReport
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class PublisherScenarioTest {

    private val stable = System.getProperty("gradlenew.offered.stable") ?: error("gradlenew.offered.stable is not set")
    private val classpath = System.getProperty("acceptance.publisherClasspath") ?: error("acceptance.publisherClasspath is not set")
    private val builderSources = System.getProperty("acceptance.builderSources") ?: error("acceptance.builderSources is not set")
    private val versionCatalog = System.getProperty("acceptance.versionCatalog") ?: error("acceptance.versionCatalog is not set")
    private val workRoot = Files.createTempDirectory("publisher-scenario").toFile()

    @Test
    fun publisherScenario() {
        val model = publishesTheRealStable()
        refusesNondeterministicOutput()
        refusesAnUnknownProjectNameForm()
        enumeratesATypeWhoseOutputDependsOnTheJavaVersion(model)
        refusesTemplatesOverTheBudget()
    }

    private fun publishesTheRealStable(): InitModel {
        val run = publish("real-stable")

        assertEquals(0, run.exitCode, run.output)
        val report = run.report()
        assertEquals(Check.entries.toSet(), report.checks.map { it.check }.toSet())
        assertTrue(report.checks.all { it.outcome == Outcome.PASSED }, report.toString())
        assertEquals(emptyList(), report.javaEnumeratedTypes)

        assertEquals(stable, VersionsJson.decode(run.site.resolve("versions.json").readText()).stable)
        val model = InitModelJson.decode(run.site.resolve("v/$stable/model.json").readText())
        val expectedKeys = TemplateCatalog.of(model).map { "${it.spec.key}.zip" }.toSet()
        assertEquals(expectedKeys, run.templateKeys())
        assertEquals(emptySet(), TemplateIndexJson.decode(run.templates.resolve("$stable/index.json").readText()).javaEnumeratedTypes)
        return model
    }

    private fun refusesNondeterministicOutput() {
        val run = publish("nondeterministic", initScript = "nondeterministic.gradle")

        assertEquals(1, run.exitCode, run.output)
        val determinism = run.report().check(Check.DETERMINISM)
        assertEquals(Outcome.FAILED, determinism.outcome)
        assertTrue(determinism.failures.any { it.subject.startsWith("java-application.") }, determinism.toString())
        assertTrue(determinism.failures.all { it.message.contains("generated-at.txt") }, determinism.toString())
        run.assertNothingPublished()
    }

    private fun refusesAnUnknownProjectNameForm() {
        val run = publish("unknown-name-form", initScript = "spaced-project-name.gradle")

        assertEquals(1, run.exitCode, run.output)
        val report = run.report()
        assertEquals(Outcome.FAILED, report.check(Check.HYGIENE).outcome)
        assertTrue(report.check(Check.HYGIENE).failures.any { it.message.contains("PROJECT.txt") }, report.toString())
        assertEquals(Outcome.FAILED, report.check(Check.DIFFERENTIAL).outcome)
        run.assertNothingPublished()
    }

    private fun enumeratesATypeWhoseOutputDependsOnTheJavaVersion(model: InitModel) {
        val run = publish("java-version-elsewhere", initScript = "java-version-comment.gradle")

        assertEquals(0, run.exitCode, run.output)
        val report = run.report()
        assertTrue(report.checks.all { it.outcome == Outcome.PASSED }, report.toString())
        assertEquals(listOf("groovy-library"), report.javaEnumeratedTypes)
        assertEquals(setOf("groovy-library"), TemplateIndexJson.decode(run.templates.resolve("$stable/index.json").readText()).javaEnumeratedTypes)

        val keys = run.templateKeys()
        val range = model.javaVersion.minimum..model.javaVersion.maximum
        val groovyLibrary = keys.filter { it.startsWith("groovy-library.") }
        val combinations = TemplateCatalog.of(model).count { it.spec.type == "groovy-library" }
        assertEquals(combinations * (range.last - range.first + 1), groovyLibrary.size)
        assertEquals(range.toSet(), groovyLibrary.map { it.removeSuffix(".zip").substringAfterLast(".java").toInt() }.toSet())
        assertTrue(keys.filterNot { it.startsWith("groovy-library.") }.none { it.contains(".java") }, keys.toString())
    }

    private fun refusesTemplatesOverTheBudget() {
        val run = publish("over-budget", initScript = "java-version-comment.gradle", budgetMegabytes = 1)

        assertEquals(1, run.exitCode, run.output)
        assertEquals(Outcome.FAILED, run.report().check(Check.BUDGET).outcome)
        run.assertNothingPublished()
    }

    private fun publish(name: String, initScript: String? = null, budgetMegabytes: Int? = null): PublisherRun {
        val dir = workRoot.resolve(name)
        val arguments = buildList {
            addAll(listOf("--stable", stable, "--rc", ""))
            addAll(listOf("--builder-dir", builderSources, "--catalog", versionCatalog))
            addAll(listOf("--output", dir.resolve("site").path, "--templates-output", dir.resolve("templates").path))
            addAll(listOf("--report-output", dir.resolve("report").path, "--work-dir", dir.resolve("work").path))
            budgetMegabytes?.let { addAll(listOf("--template-budget-mb", it.toString())) }
            initScript?.let { addAll(listOf("--gradle-argument", "--init-script", "--gradle-argument", faultInjection(it).path)) }
        }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val started = TimeSource.Monotonic.markNow()
        val process = ProcessBuilder(listOf(java, "-Xmx512m", "-cp", classpath, "gradlenew.publisher.MainKt") + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(20, TimeUnit.MINUTES), "publisher did not finish")
        println("publisher scenario step '$name': exit ${process.exitValue()} in ${started.elapsedNow().inWholeSeconds} s")
        return PublisherRun(process.exitValue(), output, dir)
    }

    private fun faultInjection(name: String): File {
        val target = workRoot.resolve("fault-injection/$name")
        target.parentFile.mkdirs()
        target.writeText(requireNotNull(javaClass.getResource("/fault-injection/$name")) { "missing init script $name" }.readText())
        return target
    }

    private inner class PublisherRun(val exitCode: Int, val output: String, dir: File) {
        val site = dir.resolve("site")
        val templates = dir.resolve("templates")
        private val reportFile = dir.resolve("report/verification-report.json")

        fun report(): VersionReport {
            assertTrue(reportFile.isFile, "no verification report was written:\n$output")
            return VerificationReportJson.decode(reportFile.readText()).versions.single { it.version == stable }
        }

        fun templateKeys(): Set<String> =
            templates.resolve(stable).list()!!.filter { it.endsWith(".zip") }.toSet()

        fun assertNothingPublished() {
            assertFalse(site.walkTopDown().any { it.isFile }, "site files were published:\n$output")
            assertFalse(templates.walkTopDown().any { it.isFile }, "templates were published:\n$output")
        }
    }

    private fun VersionReport.check(check: Check) = checks.single { it.check == check }
}
