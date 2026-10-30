package gradlenew.server

import gradlenew.adapterjson.DiscoveryIndexJson
import gradlenew.adapterjson.ErrorJson
import gradlenew.domain.agent.DiscoveryIndex
import gradlenew.domain.agent.SkillEntry
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    private val site: File = Files.createTempDirectory("site").toFile().apply {
        resolve("index.html").writeText("<!doctype html><title>test site</title>")
        resolve("versions.json").writeText("""{"stable":"9.8.2","rc":null,"previous":"8.14.6"}""")
        resolve("v/9.8.2").mkdirs()
        resolve("v/9.8.2/model.json").writeText("""{"gradleVersion":"9.8.2"}""")
        resolve("v/8.14.6").mkdirs()
        resolve("v/8.14.6/model.json").writeText("""{"gradleVersion":"8.14.6"}""")
        resolve("app").mkdirs()
        resolve("app/main.js").writeText("export {}")
        resolve("assets/fonts").mkdirs()
        resolve("assets/fonts/lato-latin-400-normal.woff2").writeBytes(byteArrayOf(0x77, 0x4f, 0x46, 0x32))
    }

    private val templates: File = Files.createTempDirectory("templates").toFile()

    private val agent: File = Files.createTempDirectory("agent").toFile().apply {
        resolve("SKILL.md").writeText("---\nname: gradle-new\ndescription: Create a new Gradle build.\n---\n\nRead {{origin}}/api/model, then {{origin}}/llms.txt.\n")
        resolve("llms.txt").writeText("# gradle-new\n\n- [Options model]({{origin}}/api/model)\n")
    }

    private val agentPaths = listOf("/.well-known/agent-skills/index.json", "/.well-known/agent-skills/gradle-new/SKILL.md", "/llms.txt")

    @Test
    fun servesPerVersionFilesAsImmutableJson() = testApplication {
        application { gradleNew(site, templates, agent) }

        val response = client.get("/v/9.8.2/model.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        assertEquals("""{"gradleVersion":"9.8.2"}""", response.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/v/9.8.1/model.json").status)
    }

    @Test
    fun servesTheModelOfAChannelOrAVersion() = testApplication {
        application { gradleNew(site, templates, agent) }

        val stable = client.get("/api/model")
        assertEquals(HttpStatusCode.OK, stable.status)
        assertEquals(ContentType.Application.Json, stable.contentType()?.withoutParameters())
        assertEquals("public, max-age=60", stable.headers[HttpHeaders.CacheControl])
        assertEquals("""{"gradleVersion":"9.8.2"}""", stable.bodyAsText())
        assertEquals("""{"gradleVersion":"9.8.2"}""", client.get("/api/model?version=stable").bodyAsText())

        val concrete = client.get("/api/model?version=9.8.2")
        assertEquals("public, max-age=31536000, immutable", concrete.headers[HttpHeaders.CacheControl])

        val previous = client.get("/api/model?version=previous")
        assertEquals(HttpStatusCode.OK, previous.status)
        assertEquals("public, max-age=60", previous.headers[HttpHeaders.CacheControl])
        assertEquals("""{"gradleVersion":"8.14.6"}""", previous.bodyAsText())
        assertEquals("public, max-age=31536000, immutable", client.get("/api/model?version=8.14.6").headers[HttpHeaders.CacheControl])

        val rc = client.get("/api/model?version=rc")
        assertEquals(HttpStatusCode.BadRequest, rc.status)
        assertEquals(ContentType.Application.Json, rc.contentType()?.withoutParameters())
        assertTrue(rc.bodyAsText().contains("No release candidate is currently offered."), rc.bodyAsText())
        assertTrue(rc.bodyAsText().contains("\"parameter\": \"version\""), rc.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/model?type=basic").status)
    }

    @Test
    fun servesTheSkillAndLlmsTxtAsTextWithTheRequestsOrigin() = testApplication {
        application { gradleNew(site, templates, agent) }

        val skill = client.get("/.well-known/agent-skills/gradle-new/SKILL.md") { header(HttpHeaders.Host, "gradle.example") }
        assertEquals(HttpStatusCode.OK, skill.status)
        assertEquals(ContentType.Text.Plain, skill.contentType()?.withoutParameters())
        assertEquals(Charsets.UTF_8, skill.contentType()?.charset())
        assertEquals("public, max-age=60", skill.headers[HttpHeaders.CacheControl])
        assertEquals("---\nname: gradle-new\ndescription: Create a new Gradle build.\n---\n\nRead https://gradle.example/api/model, then https://gradle.example/llms.txt.\n", skill.bodyAsText())

        val llms = client.get("/llms.txt") { header(HttpHeaders.Host, "localhost:8080") }
        assertEquals(HttpStatusCode.OK, llms.status)
        assertEquals(ContentType.Text.Plain, llms.contentType()?.withoutParameters())
        assertEquals("public, max-age=60", llms.headers[HttpHeaders.CacheControl])
        assertEquals("# gradle-new\n\n- [Options model](http://localhost:8080/api/model)\n", llms.bodyAsText())
    }

    @Test
    fun indexesTheSkillWithTheDigestOfTheBytesItServes() = testApplication {
        application { gradleNew(site, templates, agent) }

        for (host in listOf("gradle.example", "127.0.0.1:41234")) {
            val index = client.get("/.well-known/agent-skills/index.json") { header(HttpHeaders.Host, host) }
            assertEquals(HttpStatusCode.OK, index.status)
            assertEquals(ContentType.Application.Json, index.contentType()?.withoutParameters())
            assertEquals("public, max-age=60", index.headers[HttpHeaders.CacheControl])
            val skill = client.get("/.well-known/agent-skills/gradle-new/SKILL.md") { header(HttpHeaders.Host, host) }.bodyAsBytes()
            val digest = "sha256:" + MessageDigest.getInstance("SHA-256").digest(skill).joinToString("") { "%02x".format(it) }
            assertEquals(
                DiscoveryIndexJson.encode(DiscoveryIndex(listOf(SkillEntry("gradle-new", "Create a new Gradle build.", SkillEntry.SKILL_MD, "gradle-new/SKILL.md", digest)))),
                index.bodyAsText(),
                host,
            )
        }
    }

    @Test
    fun rejectsAHostHeaderThatIsNotAHostName() = testApplication {
        application { gradleNew(site, templates, agent) }

        for (path in agentPaths) {
            for (host in listOf("gradle.example/{{origin}}", "gradle example", "")) {
                val response = client.get(path) { header(HttpHeaders.Host, host) }
                assertEquals(HttpStatusCode.BadRequest, response.status, "$path with Host '$host'")
                assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
                assertEquals("The Host header is not a host name with an optional port.", ErrorJson.decode(response.bodyAsText()))
            }
        }
    }

    @Test
    fun servesVersionsAsJsonWithCacheHeaders() = testApplication {
        application { gradleNew(site, templates, agent) }

        val response = client.get("/api/versions")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
        assertEquals("""{"stable":"9.8.2","rc":null,"previous":"8.14.6"}""", response.bodyAsText())
    }

    @Test
    fun servesStaticFilesFromTheSiteTree() = testApplication {
        application { gradleNew(site, templates, agent) }

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
        assertEquals(File("/agent"), agentDirFrom(emptyMap()))
        assertEquals(File("/tmp/a"), agentDirFrom(mapOf("AGENT_DIR" to "/tmp/a")))
    }

    @Test
    fun everyResponseCarriesTheContentSecurityPolicy() = testApplication {
        application { gradleNew(site, templates, agent) }

        for (path in listOf("/", "/app/main.js", "/assets/fonts/lato-latin-400-normal.woff2", "/api/versions", "/v/9.8.2/model.json", "/api/generate", "/missing.txt") + agentPaths) {
            val response = client.get(path)
            assertEquals("default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'", response.headers["Content-Security-Policy"], path)
            assertEquals("nosniff", response.headers["X-Content-Type-Options"], path)
        }
    }

    @Test
    fun servesThePageAndItsScriptsFreshAndTheVendoredAssetsCached() = testApplication {
        application { gradleNew(site, templates, agent) }

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
        application { gradleNew(site, templates, agent) }

        for (path in listOf("/", "/api/versions", "/v/9.8.2/model.json", "/missing")) {
            assertEquals("max-age=31536000", client.get(path).headers["Strict-Transport-Security"], path)
        }
    }
}
