package gradlenew.publisher

import gradlenew.adaptertoolingapi.BuilderCompiler
import gradlenew.adaptertoolingapi.GradleDistribution
import gradlenew.adaptertoolingapi.InitModelRequest
import gradlenew.domain.model.OfferedVersions

fun main(args: Array<String>) {
    val arguments = Arguments.parse(args.toList())
    val publisher = SitePublisher { version ->
        val gradle = GradleDistribution(version)
        val workDir = arguments.workDir.resolve(version)
        val jars = BuilderCompiler.compile(gradle, arguments.builderSources, arguments.versionCatalog, workDir)
        InitModelRequest.request(gradle, jars, workDir)
    }
    publisher.publish(OfferedVersions(arguments.stable, arguments.rc), arguments.output)
}
