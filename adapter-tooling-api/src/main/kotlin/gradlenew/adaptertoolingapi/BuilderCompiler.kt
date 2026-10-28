package gradlenew.adaptertoolingapi

import java.io.File

data class BuilderJars(val plugin: File, val api: File)

object BuilderCompiler {

    private val excludedDirectories = setOf("build", ".gradle", ".kotlin")

    fun compile(gradle: GradleDistribution, builderSources: File, versionCatalog: File, workDir: File): BuilderJars {
        val copy = workDir.resolve("builder")
        copy.deleteRecursively()
        copySources(builderSources, copy)
        versionCatalog.copyTo(workDir.resolve("gradle/libs.versions.toml"), overwrite = true)
        copy.resolve("gradle.properties").appendText("\norg.gradle.daemon.idletimeout=120000\n")
        gradle.runTasks(copy, listOf(":builder-plugin:jar", ":builder-api:jar", "--quiet"))
        return BuilderJars(singleJar(copy.resolve("builder-plugin/build/libs")), singleJar(copy.resolve("builder-api/build/libs")))
    }

    private fun copySources(from: File, to: File) {
        from.walkTopDown()
            .onEnter { it.name !in excludedDirectories }
            .filter { it.isFile }
            .forEach { file ->
                val target = to.resolve(file.relativeTo(from))
                target.parentFile.mkdirs()
                file.copyTo(target)
            }
    }

    private fun singleJar(libs: File): File =
        libs.listFiles { file -> file.extension == "jar" }?.singleOrNull()
            ?: error("Expected exactly one jar in $libs, found ${libs.listFiles()?.map { it.name }}")
}
