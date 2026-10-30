package gradlenew.server

import gradlenew.adapterjson.ErrorJson
import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.TemplateIndexJson
import gradlenew.adapterzip.Zip
import gradlenew.domain.generation.TemplateIndex
import gradlenew.domain.model.BuildType
import gradlenew.domain.model.Choice
import gradlenew.domain.model.ComponentType
import gradlenew.domain.model.Dsl
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.JavaVersionParameter
import gradlenew.domain.model.Language
import gradlenew.domain.model.ModularizationOption
import gradlenew.domain.model.TestFramework
import gradlenew.domain.model.TestFrameworkChoice
import gradlenew.domain.model.TextParameter
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateRouteTest {

    private val model = InitModel(
        gradleVersion = "9.8.2",
        componentTypes = listOf(ComponentType("application", "Application", listOf("app"))),
        languages = listOf(Language("java", "Java")),
        dsls = listOf(Dsl("kotlin", "Kotlin", ".gradle.kts")),
        modularizationOptions = listOf(ModularizationOption("single-project", "Single application project")),
        testFrameworks = listOf(TestFramework("junit-jupiter", "JUnit Jupiter")),
        buildTypes = listOf(
            BuildType(
                id = "java-application", componentType = "application", language = "java",
                dsls = Choice(listOf("kotlin"), "kotlin"),
                modularization = Choice(listOf("single-project"), "single-project"),
                testFrameworks = listOf(TestFrameworkChoice("single-project", listOf("junit-jupiter"), "junit-jupiter")),
                supportsPackage = true, supportsJavaVersion = true, supportsProjectName = true, furtherReading = null,
            ),
        ),
        javaVersion = JavaVersionParameter(7, 21, 31),
        packageName = TextParameter("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$", "org.example"),
        projectName = TextParameter(null, null),
    )

    private val template = FileSet.of(
        FileEntry("settings.gradle.kts", "rootProject.name = \"zqxproj-name\"".encodeToByteArray(), false),
        FileEntry("app/src/main/java/zqxpkg/zqxsub/App.java", "package zqxpkg.zqxsub;".encodeToByteArray(), false),
        FileEntry("app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(97)".encodeToByteArray(), false),
        FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true),
    )

    private val templateKey = "java-application.kotlin.junit-jupiter.single-project.no-incubating.comments"

    private val site: File = Files.createTempDirectory("site").toFile().apply {
        resolve("versions.json").writeText("""{"stable":"9.8.2","rc":null}""")
        resolve("v/9.8.2").mkdirs()
        resolve("v/9.8.2/model.json").writeText(InitModelJson.encode(model))
    }

    private val agent: File = Files.createTempDirectory("agent").toFile()

    private val templates: File = Files.createTempDirectory("templates").toFile().apply {
        resolve("9.8.2").mkdirs()
        resolve("9.8.2/$templateKey.zip").writeBytes(Zip.write(template))
    }

    @Test
    fun returnsTheGeneratedBuildAsAZip() = testApplication {
        application { gradleNew(site, templates, agent) }

        val response = client.get("/api/generate?type=java-application&project-name=demo-app&package=com.acme.demo&java-version=17")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Zip, response.contentType())
        assertEquals("attachment; filename=\"demo-app.zip\"", response.headers[HttpHeaders.ContentDisposition])
        assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
        assertNotNull(response.headers[HttpHeaders.ETag])
        assertEquals(
            FileSet.of(
                FileEntry("demo-app/settings.gradle.kts", "rootProject.name = \"demo-app\"".encodeToByteArray(), false),
                FileEntry("demo-app/app/src/main/java/com/acme/demo/App.java", "package com.acme.demo;".encodeToByteArray(), false),
                FileEntry("demo-app/app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(17)".encodeToByteArray(), false),
                FileEntry("demo-app/gradlew", "#!/bin/sh".encodeToByteArray(), true),
            ),
            Zip.read(response.bodyAsBytes()),
        )
    }

    @Test
    fun marksAConcreteVersionAsImmutableAndAnswersIdenticalRequestsIdentically() = testApplication {
        application { gradleNew(site, templates, agent) }

        val first = client.get("/api/generate?version=9.8.2&type=java-application&project-name=demo")
        val second = client.get("/api/generate?version=9.8.2&type=java-application&project-name=demo")

        assertEquals("public, max-age=31536000, immutable", first.headers[HttpHeaders.CacheControl])
        assertTrue(first.bodyAsBytes().contentEquals(second.bodyAsBytes()))
        assertEquals(first.headers[HttpHeaders.ETag], second.headers[HttpHeaders.ETag])
    }

    @Test
    fun answersNotModifiedForAMatchingEntityTag() = testApplication {
        application { gradleNew(site, templates, agent) }
        val etag = client.get("/api/generate?type=java-application&project-name=demo").headers[HttpHeaders.ETag]!!

        val response = client.get("/api/generate?type=java-application&project-name=demo") { header(HttpHeaders.IfNoneMatch, etag) }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals(etag, response.headers[HttpHeaders.ETag])
        assertEquals(0, response.bodyAsBytes().size)
    }

    @Test
    fun rejectsInvalidRequestsWithTheRuleAsJson() = testApplication {
        application { gradleNew(site, templates, agent) }

        val response = client.get("/api/generate?type=java-application&project-name=demo&java-version=6")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(
            "Target Java version: '6' is not a supported target version. It must be equal to or greater than 7",
            ErrorJson.decode(response.bodyAsText()),
        )
        assertEquals("java-version", ErrorJson.parameter(response.bodyAsText()))
    }

    @Test
    fun templatesAreNotServed() = testApplication {
        application { gradleNew(site, templates, agent) }

        assertEquals(HttpStatusCode.NotFound, client.get("/v/9.8.2/templates/$templateKey.zip").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/templates/9.8.2/$templateKey.zip").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/9.8.2/$templateKey.zip").status)
    }

    @Test
    fun servesTheTemplateForTheRequestedJavaVersionOfAnEnumeratedType() = testApplication {
        val enumerated = Files.createTempDirectory("enumerated").toFile().apply {
            resolve("9.8.2").mkdirs()
            resolve("9.8.2/index.json").writeText(TemplateIndexJson.encode(TemplateIndex(setOf("java-application"))))
            resolve("9.8.2/$templateKey.java17.zip").writeBytes(
                Zip.write(
                    FileSet.of(
                        FileEntry("settings.gradle.kts", "rootProject.name = \"zqxproj-name\"".encodeToByteArray(), false),
                        FileEntry("app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(17)\n// targets Java 17".encodeToByteArray(), false),
                    ),
                ),
            )
        }
        application { gradleNew(site, enumerated, agent) }

        val response = client.get("/api/generate?type=java-application&project-name=demo&java-version=17")
        val outOfRange = client.get("/api/generate?type=java-application&project-name=demo&java-version=32")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            FileSet.of(
                FileEntry("demo/settings.gradle.kts", "rootProject.name = \"demo\"".encodeToByteArray(), false),
                FileEntry("demo/app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(17)\n// targets Java 17".encodeToByteArray(), false),
            ),
            Zip.read(response.bodyAsBytes()),
        )
        assertEquals(HttpStatusCode.BadRequest, outOfRange.status)
        assertEquals(
            "Target Java version: '32' is not offered for 'java-application' build type - it must be between 7 and 31.",
            ErrorJson.decode(outOfRange.bodyAsText()),
        )
        assertEquals(HttpStatusCode.NotFound, client.get("/9.8.2/index.json").status)
    }

    @Test
    fun rejectsRepeatedParameters() = testApplication {
        application { gradleNew(site, templates, agent) }

        val response = client.get("/api/generate?type=java-application&project-name=a&project-name=b")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Option 'project-name' is given more than once.", ErrorJson.decode(response.bodyAsText()))
    }

    @Test
    fun theDefaultLimitsAreSixtyBuildsAndSixHundredRequestsPerMinutePerClient() {
        assertEquals(RateLimits(generatePerMinute = 60, overallPerMinute = 600), RateLimits.DEFAULT)
    }

    @Test
    fun limitsGeneratedBuildsPerClientWithRetryAfterAndAJsonMessage() = testApplication {
        application { gradleNew(site, templates, agent, RateLimits(generatePerMinute = 3, overallPerMinute = 100)) }
        val url = "/api/generate?type=java-application&project-name=demo"

        repeat(3) { assertEquals(HttpStatusCode.OK, client.get(url) { header("X-Forwarded-For", "203.0.113.7") }.status) }
        val limited = client.get(url) { header("X-Forwarded-For", "203.0.113.7") }

        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        val retryAfter = assertNotNull(limited.headers[HttpHeaders.RetryAfter]).toLong()
        assertTrue(retryAfter in 1..60, "Retry-After $retryAfter")
        assertEquals(ContentType.Application.Json, limited.contentType()?.withoutParameters())
        assertEquals("Too many builds were requested from your address in a short time. Try again in $retryAfter seconds.", ErrorJson.decode(limited.bodyAsText()))
        assertEquals(HttpStatusCode.OK, client.get(url) { header("X-Forwarded-For", "198.51.100.4") }.status, "another client has its own limit")
    }

    @Test
    fun theClientIsTheRightmostForwardedForEntrySoSpoofedEntriesDoNotHelp() = testApplication {
        application { gradleNew(site, templates, agent, RateLimits(generatePerMinute = 2, overallPerMinute = 100)) }
        val url = "/api/generate?type=java-application&project-name=demo"

        assertEquals(HttpStatusCode.OK, client.get(url) { header("X-Forwarded-For", "10.0.0.1, 203.0.113.7") }.status)
        assertEquals(HttpStatusCode.OK, client.get(url) { header("X-Forwarded-For", "10.0.0.2, 203.0.113.7") }.status)

        assertEquals(HttpStatusCode.TooManyRequests, client.get(url) { header("X-Forwarded-For", "10.0.0.3,203.0.113.7") }.status)
    }

    @Test
    fun limitsEveryRequestOverallPerClient() = testApplication {
        application { gradleNew(site, templates, agent, RateLimits(generatePerMinute = 100, overallPerMinute = 4)) }

        repeat(4) { assertEquals(HttpStatusCode.OK, client.get("/api/versions").status) }
        val limited = client.get("/api/versions")

        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/generate?type=java-application&project-name=demo").status, "the overall limit covers the API too")
    }
}
