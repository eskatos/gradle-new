package gradlenew.publisher

import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterjson.VerificationReportJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.TemplateIndex
import gradlenew.domain.model.InitModel
import gradlenew.verification.Check
import gradlenew.verification.CheckResult
import gradlenew.verification.DistributionCheck
import gradlenew.verification.InitGenerator
import gradlenew.verification.ModelCheck
import gradlenew.verification.TemplateSink
import gradlenew.verification.Verification
import gradlenew.verification.VerificationReport
import gradlenew.verification.VersionReport
import gradlenew.verification.WrapperDigest
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PublishResult(val exitCode: Int, val report: VerificationReport, val summary: String)

class SitePublisher(
    private val distributionFor: (version: String) -> DistributionOutcome,
    private val modelFor: (VerifiedDistribution) -> InitModel,
    private val initFor: (VerifiedDistribution) -> InitGenerator,
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
        val distribution = when (val outcome = distributionFor(version)) {
            is DistributionOutcome.Rejected -> return VersionReport(version, listOf(outcome.check) + Check.entries.drop(1).map { CheckResult.skipped(it) }, emptyList())
            is DistributionOutcome.Verified -> outcome.distribution
        }
        val distributionCheck = DistributionCheck.of(version, distribution.checksums.distribution, distribution.checksums.distribution)
        val model = try {
            modelFor(distribution).also { require(it.gradleVersion == version) { "the model reports Gradle ${it.gradleVersion}, expected $version" } }
        } catch (failure: Exception) {
            return unavailable(version, distributionCheck, distribution, failure.message ?: failure.toString())
        }
        val site = staging.resolve("site").apply { mkdirs() }
        val templates = staging.resolve("templates").apply { mkdirs() }
        val sink = TemplateSink { entry, files ->
            val zip = Zip.write(files)
            templates.resolve("${entry.spec.key}.zip").writeBytes(zip)
            zip.size.toLong()
        }
        val wrapper = WrapperDigest(distribution.checksums.wrapper, Sha256::hex)
        val verified = Verification(initFor(distribution), sink, templateBudgetBytes, wrapper, sizeOf = { Zip.write(it).size.toLong() }).verify(model)
        val report = VersionReport(version, listOf(distributionCheck) + verified.checks, verified.javaEnumeratedTypes, distribution.source)
        if (report.passed) {
            site.resolve("model.json").writeText(InitModelJson.encode(model) + "\n")
            templates.resolve("index.json").writeText(TemplateIndexJson.encode(TemplateIndex(report.javaEnumeratedTypes.toSet())) + "\n")
        }
        return report
    }

    private fun unavailable(version: String, distributionCheck: CheckResult, distribution: VerifiedDistribution, reason: String) = VersionReport(
        version,
        listOf(distributionCheck, ModelCheck.unavailable(version, reason)) + Check.entries.drop(2).map { CheckResult.skipped(it) },
        emptyList(),
        distribution.source,
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
