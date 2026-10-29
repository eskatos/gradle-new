package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.TemplateIndex
import gradlenew.domain.model.InitModel
import gradlenew.verification.Check
import gradlenew.verification.CheckResult
import gradlenew.verification.InitGenerator
import gradlenew.verification.ModelCheck
import gradlenew.verification.TemplateSink
import gradlenew.verification.Verification
import gradlenew.verification.VerificationReport
import gradlenew.verification.VersionReport
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PublishResult(val exitCode: Int, val report: VerificationReport, val summary: String)

class SitePublisher(
    private val modelFor: (version: String) -> InitModel,
    private val initFor: (version: String) -> InitGenerator,
    private val templateBudgetBytes: Long,
) {

    fun publish(version: String, output: File, templatesOutput: File, reportOutput: File, workDir: File): PublishResult {
        val staging = workDir.resolve("staging")
        for (dir in listOf(output, templatesOutput, reportOutput, staging)) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
        val versionReport = verify(version, staging)
        if (versionReport.passed) {
            move(staging.resolve("site"), output.resolve("v/$version"))
            move(staging.resolve("templates"), templatesOutput.resolve(version))
        }
        staging.deleteRecursively()
        val report = VerificationReport(listOf(versionReport))
        reportOutput.resolve("verification-report.json").writeText(VerificationReportJson.encode(report) + "\n")
        return PublishResult(if (versionReport.passed) 0 else 1, report, report.summary())
    }

    private fun verify(version: String, staging: File): VersionReport {
        val model = try {
            modelFor(version).also { require(it.gradleVersion == version) { "the model reports Gradle ${it.gradleVersion}, expected $version" } }
        } catch (failure: Exception) {
            return unavailable(version, failure.message ?: failure.toString())
        }
        val site = staging.resolve("site").apply { mkdirs() }
        val templates = staging.resolve("templates").apply { mkdirs() }
        val sink = TemplateSink { entry, files ->
            val zip = Zip.write(files)
            templates.resolve("${entry.spec.key}.zip").writeBytes(zip)
            zip.size.toLong()
        }
        val report = Verification(initFor(version), sink, templateBudgetBytes, sizeOf = { Zip.write(it).size.toLong() }).verify(model)
        if (report.passed) {
            site.resolve("model.json").writeText(InitModelJson.encode(model) + "\n")
            templates.resolve("index.json").writeText(TemplateIndexJson.encode(TemplateIndex(report.javaEnumeratedTypes.toSet())) + "\n")
        }
        return report
    }

    private fun unavailable(version: String, reason: String) = VersionReport(
        version,
        listOf(ModelCheck.unavailable(version, reason)) + Check.entries.drop(1).map { CheckResult.skipped(it) },
        emptyList(),
    )

    private fun move(from: File, to: File) {
        to.parentFile.mkdirs()
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            from.copyRecursively(to, overwrite = true)
            from.deleteRecursively()
        }
    }
}
