package gradlenew.publisher

import gradlenew.adaptertoolingapi.BuilderCompiler
import gradlenew.adaptertoolingapi.GradleDistribution
import gradlenew.adaptertoolingapi.InitModelRequest
import gradlenew.adaptertoolingapi.InitRejected
import gradlenew.adaptertoolingapi.InitRunner
import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.model.OfferedVersions
import gradlenew.verification.InitGenerator
import gradlenew.verification.InitOutcome
import kotlin.system.exitProcess
import kotlin.time.TimeSource

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "propose" -> exitProcess(ProposeCommand().run(args.drop(1), System.out, System.err))
        else -> publish(args)
    }
}

private fun publish(args: Array<String>) {
    val arguments = Arguments.parse(args.toList())
    val started = TimeSource.Monotonic.markNow()
    var inits = 0
    val publisher = SitePublisher(
        modelFor = { version ->
            val gradle = GradleDistribution(version, arguments.gradleArguments)
            val workDir = arguments.workDir.resolve("model/$version")
            val jars = BuilderCompiler.compile(gradle, arguments.builderSources, arguments.versionCatalog, workDir)
            InitModelRequest.request(gradle, jars, workDir)
        },
        initFor = { version ->
            val init = InitRunner(GradleDistribution(version, arguments.gradleArguments))
            InitGenerator { spec, inputs ->
                inits++
                try {
                    InitOutcome.Generated(init.run(CatalogEntry(spec, inputs).initArguments()))
                } catch (rejected: InitRejected) {
                    InitOutcome.Rejected(rejected.reason)
                }
            }
        },
        templateBudgetBytes = arguments.templateBudgetBytes,
    )
    val result = publisher.publish(
        OfferedVersions(arguments.stable, arguments.rc),
        arguments.output,
        arguments.templatesOutput,
        arguments.reportOutput,
        arguments.workDir,
    )
    print(result.summary)
    println("Ran gradle init $inits times in ${started.elapsedNow().inWholeSeconds} s; report at ${arguments.reportOutput.resolve("verification-report.json")}")
    exitProcess(result.exitCode)
}
