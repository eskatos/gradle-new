package gradlenew.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

fun main() {
    val site = File(System.getenv("SITE_DIR") ?: "/site")
    embeddedServer(CIO, port = portFrom(System.getenv()), host = "0.0.0.0") { gradleNew(site) }.start(wait = true)
}

fun portFrom(environment: Map<String, String>): Int = environment["PORT"]?.toInt() ?: 8080
