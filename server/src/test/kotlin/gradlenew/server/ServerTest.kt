package gradlenew.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    private val site: File = Files.createTempDirectory("site").toFile().apply {
        resolve("index.html").writeText("<!doctype html><title>test site</title>")
        resolve("versions.json").writeText("""{"stable":"9.8.2","rc":null}""")
        resolve("v/9.8.2").mkdirs()
        resolve("v/9.8.2/model.json").writeText("""{"gradleVersion":"9.8.2"}""")
        resolve("app").mkdirs()
        resolve("app/main.js").writeText("export {}")
        resolve("assets/fonts").mkdirs()
        resolve("assets/fonts/lato-latin-400-normal.woff2").writeBytes(byteArrayOf(0x77, 0x4f, 0x46, 0x32))
    }

    private val templates: File = Files.createTempDirectory("templates").toFile()

    @Test
    fun servesPerVersionFilesAsImmutableJson() = testApplication {
        application { gradleNew(site, templates) }

        val response = client.get("/v/9.8.2/model.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        assertEquals("""{"gradleVersion":"9.8.2"}""", response.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/v/9.8.1/model.json").status)
    }

    @Test
    fun servesVersionsAsJsonWithCacheHeaders() = testApplication {
        application { gradleNew(site, templates) }

        val response = client.get("/api/versions")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
        assertEquals("""{"stable":"9.8.2","rc":null}""", response.bodyAsText())
    }

    @Test
    fun servesStaticFilesFromTheSiteTree() = testApplication {
        application { gradleNew(site, templates) }

        val index = client.get("/")
        val missing = client.get("/missing.txt")

        assertEquals(HttpStatusCode.OK, index.status)
        assertEquals("<!doctype html><title>test site</title>", index.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    @Test
    fun readsPortFromTheEnvironmentWithADefault() {
        assertEquals(8080, portFrom(emptyMap()))
        assertEquals(9000, portFrom(mapOf("PORT" to "9000")))
    }

    @Test
    fun readsTheContentDirectoriesFromTheEnvironmentWithDefaults() {
        assertEquals(File("/site"), siteDirFrom(emptyMap()))
        assertEquals(File("/templates"), templatesDirFrom(emptyMap()))
        assertEquals(File("/tmp/s"), siteDirFrom(mapOf("SITE_DIR" to "/tmp/s")))
        assertEquals(File("/tmp/t"), templatesDirFrom(mapOf("TEMPLATES_DIR" to "/tmp/t")))
    }

    @Test
    fun everyResponseCarriesTheContentSecurityPolicy() = testApplication {
        application { gradleNew(site, templates) }

        for (path in listOf("/", "/app/main.js", "/assets/fonts/lato-latin-400-normal.woff2", "/api/versions", "/v/9.8.2/model.json", "/api/generate", "/missing.txt")) {
            val response = client.get(path)
            assertEquals("default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'", response.headers["Content-Security-Policy"], path)
            assertEquals("nosniff", response.headers["X-Content-Type-Options"], path)
        }
    }

    @Test
    fun servesThePageAndItsScriptsFreshAndTheVendoredAssetsCached() = testApplication {
        application { gradleNew(site, templates) }

        val index = client.get("/")
        assertEquals(ContentType.Text.Html, index.contentType()?.withoutParameters())
        assertEquals("no-cache", index.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", client.get("/app/main.js").headers[HttpHeaders.CacheControl])
        val font = client.get("/assets/fonts/lato-latin-400-normal.woff2")
        assertEquals("public, max-age=86400", font.headers[HttpHeaders.CacheControl])
        assertEquals("font/woff2", font.contentType()?.withoutParameters()?.toString())
    }

    @Test
    fun asksBrowsersToUseHttpsForAYearOnThisHostOnly() = testApplication {
        application { gradleNew(site, templates) }

        for (path in listOf("/", "/api/versions", "/v/9.8.2/model.json", "/missing")) {
            assertEquals("max-age=31536000", client.get(path).headers["Strict-Transport-Security"], path)
        }
    }
}
