package gradlenew.server

import gradlenew.adapterjson.ErrorJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.GenerationResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.flattenEntries
import java.io.File
import java.security.MessageDigest

private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val SHORT_LIVED = "public, max-age=60"
private const val VENDORED = "public, max-age=86400"
private const val REVALIDATE = "no-cache"
private const val CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

private val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        call.response.header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        call.response.header("X-Content-Type-Options", "nosniff")
    }
}

fun Application.gradleNew(siteDir: File, templatesDir: File) {
    val site = Site(siteDir, templatesDir)
    install(SecurityHeaders)
    routing {
        get("/api/versions") {
            call.response.header(HttpHeaders.CacheControl, SHORT_LIVED)
            call.respondFile(File(siteDir, "versions.json"))
        }
        get("/api/generate") {
            when (val result = site.generation.generate(call.request.queryParameters.flattenEntries())) {
                is GenerationResult.Rejected -> call.respondText(ErrorJson.encode(result.message, result.parameter), ContentType.Application.Json, HttpStatusCode.BadRequest)
                is GenerationResult.Generated -> {
                    val zip = Zip.write(result.files)
                    val etag = entityTag(zip)
                    call.response.header(HttpHeaders.ETag, etag)
                    call.response.header(HttpHeaders.CacheControl, if (result.immutable) IMMUTABLE else SHORT_LIVED)
                    if (call.request.headers.getAll(HttpHeaders.IfNoneMatch).orEmpty().flatMap { it.split(',') }.any { it.trim() == etag }) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {
                        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${result.filename}\"")
                        call.respondBytes(zip, ContentType.Application.Zip)
                    }
                }
            }
        }
        staticFiles("/v", File(siteDir, "v")) {
            contentType { ContentType.Application.Json }
            modify { _, call -> call.response.header(HttpHeaders.CacheControl, IMMUTABLE) }
        }
        staticFiles("/", siteDir, index = "index.html") {
            contentType { file -> contentTypeOf(file) }
            modify { file, call ->
                val vendored = file.relativeTo(siteDir).invariantSeparatorsPath.startsWith("assets/")
                call.response.header(HttpHeaders.CacheControl, if (vendored) VENDORED else REVALIDATE)
            }
        }
    }
}

private fun contentTypeOf(file: File): ContentType? = when (file.extension) {
    "json" -> ContentType.Application.Json
    "woff2" -> ContentType("font", "woff2")
    "svg" -> ContentType.Image.SVG
    else -> null
}

private fun entityTag(bytes: ByteArray): String =
    "\"" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + "\""
