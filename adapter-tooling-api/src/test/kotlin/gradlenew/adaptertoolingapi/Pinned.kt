package gradlenew.adaptertoolingapi

import java.io.File
import java.nio.file.Files

object Pinned {
    val stable: String = System.getProperty("gradlenew.offered.stable") ?: error("gradlenew.offered.stable is not set")
    val rc: String? = System.getProperty("gradlenew.offered.rc")
    val versions: List<String> = listOfNotNull(stable, rc)
    val builderSources: File = File(System.getProperty("gradlenew.builderSources") ?: "../builder").absoluteFile
    val versionCatalog: File = File(System.getProperty("gradlenew.versionCatalog") ?: "../gradle/libs.versions.toml").absoluteFile
}

fun emptyBuild(): File =
    Files.createTempDirectory("empty-build").toFile().apply {
        resolve("settings.gradle.kts").writeText("")
        resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx256m\norg.gradle.daemon.idletimeout=120000\n")
    }
