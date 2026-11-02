package gradlenew.server

import gradlenew.adapterjson.ErrorJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaintenanceTest {

    private val page = "<!doctype html><title>gradle-new</title><p>gradle-new is not available at the moment."

    private val site: File = Files.createTempDirectory("site").toFile().apply {
        resolve("index.html").writeText("<!doctype html><title>test site</title>")
        resolve("maintenance.html").writeText(page)
        resolve("versions.json").writeText("""{"stable":"9.8.2","rc":null,"previous":"8.14.6"}""")
        resolve("v/9.8.2").mkdirs()
        resolve("v/9.8.2/model.json").writeText("""{"gradleVersion":"9.8.2"}""")
        resolve("app").mkdirs()
        resolve("app/main.js").writeText("export {}")
        resolve("app/styles.css").writeText("body {}")
        resolve("assets/fonts").mkdirs()
        resolve("assets/fonts/lato-latin-400-normal.woff2").writeBytes(byteArrayOf(0x77, 0x4f, 0x46, 0x32))
        resolve("assets/styles.0123456789ab.css").writeText("body {}")
    }

    private val templates: File = Files.createTempDirectory("templates").toFile()

    private val agent: File = Files.createTempDirectory("agent").toFile().apply {
        resolve("SKILL.md").writeText("---\nname: gradle-new\ndescription: Create a new Gradle build.\n---\n")
        resolve("llms.txt").writeText("# gradle-new\n")
    }

    @Test
    fun answersEveryRequestWithServiceUnavailableAndTheMaintenancePageForBrowsers() = testApplication {
        application { gradleNew(site, templates, agent, maintenance = true) }

        for (path in listOf("/", "/index.html", "/app/main.js", "/anything/else")) {
            val response = client.get(path)
            assertUnavailable(response.status, response.headers, path)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters(), path)
            assertEquals(page, response.bodyAsText(), path)
        }
    }

    @Test
    fun answersApiAndJsonRequestsWithTheErrorJson() = testApplication {
        application { gradleNew(site, templates, agent, maintenance = true) }

        for (path in listOf("/api/versions", "/api/model", "/api/generate?type=basic&project-name=demo", "/v/9.8.2/model.json", "/.well-known/agent-skills/index.json")) {
            val response = client.get(path)
            assertUnavailable(response.status, response.headers, path)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), path)
            assertEquals(ErrorJson.encode(MAINTENANCE_MESSAGE), response.bodyAsText(), path)
        }
    }

    @Test
    fun answersTheAgentTextFilesWithTheMessageAsText() = testApplication {
        application { gradleNew(site, templates, agent, maintenance = true) }

        for (path in listOf("/llms.txt", "/.well-known/agent-skills/gradle-new/SKILL.md")) {
            val response = client.get(path)
            assertUnavailable(response.status, response.headers, path)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters(), path)
            assertEquals(MAINTENANCE_MESSAGE + "\n", response.bodyAsText(), path)
        }
    }

    @Test
    fun servesWhatTheMaintenancePageNeeds() = testApplication {
        application { gradleNew(site, templates, agent, maintenance = true) }

        assertEquals(HttpStatusCode.OK, client.get("/assets/fonts/lato-latin-400-normal.woff2").status)
        assertEquals(HttpStatusCode.OK, client.get("/app/styles.css").status)
        val stylesheet = client.get("/assets/styles.0123456789ab.css")
        assertEquals(HttpStatusCode.OK, stylesheet.status)
        assertEquals("public, max-age=31536000, immutable", stylesheet.headers[HttpHeaders.CacheControl])
        assertEquals("max-age=31536000", client.get("/").headers["Strict-Transport-Security"])
        assertEquals("nosniff", client.get("/api/versions").headers["X-Content-Type-Options"])
    }

    @Test
    fun servesNormallyWhenNotInMaintenance() = testApplication {
        application { gradleNew(site, templates, agent) }

        assertEquals(HttpStatusCode.OK, client.get("/").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/versions").status)
    }

    @Test
    fun readsMaintenanceFromTheEnvironment() {
        assertFalse(maintenanceFrom(emptyMap()))
        assertFalse(maintenanceFrom(mapOf("MAINTENANCE" to "off")))
        assertTrue(maintenanceFrom(mapOf("MAINTENANCE" to "on")))
    }

    private fun assertUnavailable(status: HttpStatusCode, headers: Headers, path: String) {
        assertEquals(HttpStatusCode.ServiceUnavailable, status, path)
        assertEquals("3600", headers[HttpHeaders.RetryAfter], path)
        assertEquals("no-store", headers[HttpHeaders.CacheControl], path)
    }
}
