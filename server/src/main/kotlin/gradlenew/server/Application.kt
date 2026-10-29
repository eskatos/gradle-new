package gradlenew.server

import gradlenew.adapterjson.ErrorJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.GenerationResult
import gradlenew.domain.generation.ModelLookup
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.path
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
import kotlin.time.Duration.Companion.minutes

private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val SHORT_LIVED = "public, max-age=60"
private const val VENDORED = "public, max-age=86400"
private const val REVALIDATE = "no-cache"
private const val CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
private const val STRICT_TRANSPORT_SECURITY = "max-age=31536000"
private const val GENERATE_PATH = "/api/generate"
private val GENERATE = RateLimitName("generate")

data class RateLimits(val generatePerMinute: Int, val overallPerMinute: Int) {
    companion object {
        val DEFAULT = RateLimits(generatePerMinute = 60, overallPerMinute = 600)
    }
}

private val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        call.response.header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY)
    }
}

private val TooManyBuildsBody = createApplicationPlugin("TooManyBuildsBody") {
    onCallRespond { call, body ->
        if (body == HttpStatusCode.TooManyRequests && call.request.path() == GENERATE_PATH) {
            val retryAfter = call.response.headers[HttpHeaders.RetryAfter]
            val message = "Too many builds were requested from your address in a short time. Try again in ${retryAfter ?: "a few"} seconds."
            transformBody { TextContent(ErrorJson.encode(message), ContentType.Application.Json, HttpStatusCode.TooManyRequests) }
        }
    }
}

internal fun clientOf(call: ApplicationCall): String =
    call.request.headers.getAll(HttpHeaders.XForwardedFor).orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() }
        ?: call.request.local.remoteAddress

fun Application.gradleNew(siteDir: File, templatesDir: File, limits: RateLimits = RateLimits.DEFAULT) {
    val site = Site(siteDir, templatesDir)
    install(SecurityHeaders)
    install(TooManyBuildsBody)
    install(RateLimit) {
        global {
            rateLimiter(limit = limits.overallPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> clientOf(call) }
        }
        register(GENERATE) {
            rateLimiter(limit = limits.generatePerMinute, refillPeriod = 1.minutes)
            requestKey { call -> clientOf(call) }
        }
    }
    routing {
        get("/api/versions") {
            call.response.header(HttpHeaders.CacheControl, SHORT_LIVED)
            call.respondFile(File(siteDir, "versions.json"))
        }
        get("/api/model") {
            when (val lookup = ModelLookup.resolve(call.request.queryParameters.flattenEntries(), site.offered)) {
                is ModelLookup.Rejected -> call.respondText(ErrorJson.encode(lookup.message, lookup.parameter), ContentType.Application.Json, HttpStatusCode.BadRequest)
                is ModelLookup.Found -> {
                    call.response.header(HttpHeaders.CacheControl, if (lookup.immutable) IMMUTABLE else SHORT_LIVED)
                    call.respondBytes(site.modelFile(lookup.version).readBytes(), ContentType.Application.Json)
                }
            }
        }
        rateLimit(GENERATE) {
            get(GENERATE_PATH) {
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
