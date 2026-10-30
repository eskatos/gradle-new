package gradlenew.adaptertoolingapi

import java.io.File
import java.nio.file.Files

object Pinned {
    val stable: String = System.getProperty("gradlenew.offered.stable") ?: error("gradlenew.offered.stable is not set")
    val rc: String? = System.getProperty("gradlenew.offered.rc")
    val previous: String? = System.getProperty("gradlenew.offered.previous")
    val versions: List<String> = listOfNotNull(stable, rc, previous)
    val builderSources: File = File(System.getProperty("gradlenew.builderSources") ?: "../builder").absoluteFile
    val versionCatalog: File = File(System.getProperty("gradlenew.versionCatalog") ?: "../gradle/libs.versions.toml").absoluteFile

    fun javaHome(version: String): File? {
        val slot = mapOf(stable to "stable", rc to "rc", previous to "previous")[version] ?: return null
        return System.getProperty("gradlenew.offered.$slot.java-home")?.let(::File)
    }

    fun distribution(version: String): GradleDistribution = GradleDistribution(version, javaHome = javaHome(version))
}

fun emptyBuild(): File =
    Files.createTempDirectory("empty-build").toFile().apply {
        resolve("settings.gradle.kts").writeText("")
    }
