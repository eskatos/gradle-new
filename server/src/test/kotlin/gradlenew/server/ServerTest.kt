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
    }

    @Test
    fun servesVersionsAsJsonWithCacheHeaders() = testApplication {
        application { gradleNew(site) }

        val response = client.get("/api/versions")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
        assertEquals("""{"stable":"9.8.2","rc":null}""", response.bodyAsText())
    }

    @Test
    fun servesStaticFilesFromTheSiteTree() = testApplication {
        application { gradleNew(site) }

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
}
