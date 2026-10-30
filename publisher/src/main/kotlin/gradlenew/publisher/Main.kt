package gradlenew.publisher

import gradlenew.adaptertoolingapi.BuilderCompiler
import gradlenew.adaptertoolingapi.GradleDistribution
import gradlenew.adaptertoolingapi.InitModelRequest
import gradlenew.adaptertoolingapi.InitRejected
import gradlenew.adaptertoolingapi.InitRunner
import gradlenew.domain.generation.CatalogEntry
import gradlenew.verification.InitGenerator
import gradlenew.verification.InitOutcome
import kotlin.system.exitProcess
import kotlin.time.TimeSource

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "publish" -> publish(args.drop(1))
        "index" -> IndexCommand.run(args.drop(1))
        "propose" -> exitProcess(ProposeCommand().run(args.drop(1), System.out, System.err))
        else -> {
            System.err.println("usage: publish --version <v> [--java-home <dir>] ... | index --stable <v> [--rc <v>] --previous <v> --output <dir> | ${ProposeCommand.USAGE.removePrefix("usage: ")}")
            exitProcess(1)
        }
    }
}

private fun publish(args: List<String>) {
    val arguments = Arguments.parse(args)
    val started = TimeSource.Monotonic.markNow()
    var inits = 0
    val distributions = Distributions(arguments.distributionCache, arguments.distributionsUrl)
    val publisher = SitePublisher(
        distributionFor = { version ->
            val obtained = TimeSource.Monotonic.markNow()
            distributions.obtain(version, arguments.checksums).also {
                println("Obtained and verified the Gradle $version distribution in ${obtained.elapsedNow().inWholeMilliseconds} ms")
            }
        },
        modelFor = { distribution ->
            val gradle = GradleDistribution(distribution.version, arguments.gradleArguments, distribution.zip, arguments.javaHome)
            val workDir = arguments.workDir.resolve("model/${distribution.version}")
            val jars = BuilderCompiler.compile(gradle, arguments.builderSources, arguments.versionCatalog, workDir)
            InitModelRequest.request(gradle, jars, workDir)
        },
        initFor = { distribution ->
            val init = InitRunner(GradleDistribution(distribution.version, arguments.gradleArguments, distribution.zip, arguments.javaHome))
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
        arguments.version,
        arguments.output,
        arguments.templatesOutput,
        arguments.reportOutput,
        arguments.workDir,
    )
    print(result.summary)
    println("Ran gradle init $inits times in ${started.elapsedNow().inWholeSeconds} s; report at ${arguments.reportOutput.resolve("verification-report.json")}")
    exitProcess(result.exitCode)
}
