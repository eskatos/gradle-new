package gradlenew.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

fun main() {
    val environment = System.getenv()
    embeddedServer(CIO, port = portFrom(environment), host = "0.0.0.0") {
        gradleNew(siteDirFrom(environment), templatesDirFrom(environment))
    }.start(wait = true)
}

fun portFrom(environment: Map<String, String>): Int = environment["PORT"]?.toInt() ?: 8080

fun siteDirFrom(environment: Map<String, String>): File = File(environment["SITE_DIR"] ?: "/site")

fun templatesDirFrom(environment: Map<String, String>): File = File(environment["TEMPLATES_DIR"] ?: "/templates")
