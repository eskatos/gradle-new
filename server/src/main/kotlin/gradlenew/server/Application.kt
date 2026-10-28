package gradlenew.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

fun Application.gradleNew(site: File) {
    routing {
        get("/api/versions") {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=60")
            call.respondFile(File(site, "versions.json"))
        }
        staticFiles("/v", File(site, "v")) {
            contentType { ContentType.Application.Json }
            modify { _, call -> call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable") }
        }
        staticFiles("/", site, index = "index.html") {
            contentType { file -> if (file.name.endsWith(".json")) ContentType.Application.Json else null }
        }
    }
}
