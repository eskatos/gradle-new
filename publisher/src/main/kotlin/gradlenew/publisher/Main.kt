package gradlenew.publisher

import gradlenew.adaptertoolingapi.BuilderCompiler
import gradlenew.adaptertoolingapi.GradleDistribution
import gradlenew.adaptertoolingapi.InitModelRequest
import gradlenew.adaptertoolingapi.InitRunner
import gradlenew.domain.model.OfferedVersions
import kotlin.time.TimeSource

fun main(args: Array<String>) {
    val arguments = Arguments.parse(args.toList())
    val started = TimeSource.Monotonic.markNow()
    var templates = 0
    val publisher = SitePublisher(
        modelFor = { version ->
            val gradle = GradleDistribution(version)
            val workDir = arguments.workDir.resolve(version)
            val jars = BuilderCompiler.compile(gradle, arguments.builderSources, arguments.versionCatalog, workDir)
            InitModelRequest.request(gradle, jars, workDir)
        },
        templateFor = { version, entry ->
            templates++
            InitRunner(GradleDistribution(version)).run(entry.initArguments())
        },
    )
    publisher.publish(OfferedVersions(arguments.stable, arguments.rc), arguments.output)
    println("Published $templates templates in ${started.elapsedNow().inWholeSeconds} s")
}
