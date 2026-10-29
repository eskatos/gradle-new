package gradlenew.server

import gradlenew.adapterjson.ErrorJson
import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterzip.Zip
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
        javaVersion = JavaVersionParameter(7, 21),
        packageName = TextParameter("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$", "org.example"),
        projectName = TextParameter(null, null),
    )

    private val template = FileSet.of(
        FileEntry("settings.gradle.kts", "rootProject.name = \"zqxproj-name\"".encodeToByteArray(), false),
        FileEntry("app/src/main/java/zqxpkg/zqxsub/App.java", "package zqxpkg.zqxsub;".encodeToByteArray(), false),
        FileEntry("app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(97)".encodeToByteArray(), false),
        FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true),
    )

    private val site: File = Files.createTempDirectory("site").toFile().apply {
        resolve("versions.json").writeText("""{"stable":"9.8.2","rc":null}""")
        resolve("v/9.8.2/templates").mkdirs()
        resolve("v/9.8.2/model.json").writeText(InitModelJson.encode(model))
        resolve("v/9.8.2/templates/java-application.kotlin.junit-jupiter.single-project.no-incubating.comments.zip").writeBytes(Zip.write(template))
    }

    @Test
    fun returnsTheGeneratedBuildAsAZip() = testApplication {
        application { gradleNew(site) }

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
        application { gradleNew(site) }

        val first = client.get("/api/generate?version=9.8.2&type=java-application&project-name=demo")
        val second = client.get("/api/generate?version=9.8.2&type=java-application&project-name=demo")

        assertEquals("public, max-age=31536000, immutable", first.headers[HttpHeaders.CacheControl])
        assertTrue(first.bodyAsBytes().contentEquals(second.bodyAsBytes()))
        assertEquals(first.headers[HttpHeaders.ETag], second.headers[HttpHeaders.ETag])
    }

    @Test
    fun answersNotModifiedForAMatchingEntityTag() = testApplication {
        application { gradleNew(site) }
        val etag = client.get("/api/generate?type=java-application&project-name=demo").headers[HttpHeaders.ETag]!!

        val response = client.get("/api/generate?type=java-application&project-name=demo") { header(HttpHeaders.IfNoneMatch, etag) }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals(etag, response.headers[HttpHeaders.ETag])
        assertEquals(0, response.bodyAsBytes().size)
    }

    @Test
    fun rejectsInvalidRequestsWithTheRuleAsJson() = testApplication {
        application { gradleNew(site) }

        val response = client.get("/api/generate?type=java-application&project-name=demo&java-version=6")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(
            "Target Java version: '6' is not a supported target version. It must be equal to or greater than 7",
            ErrorJson.decode(response.bodyAsText()),
        )
    }

    @Test
    fun doesNotServeTheTemplatesDirectly() = testApplication {
        application { gradleNew(site) }

        assertEquals(HttpStatusCode.Forbidden, client.get("/v/9.8.2/templates/java-application.kotlin.junit-jupiter.single-project.no-incubating.comments.zip").status)
    }

    @Test
    fun rejectsRepeatedParameters() = testApplication {
        application { gradleNew(site) }

        val response = client.get("/api/generate?type=java-application&project-name=a&project-name=b")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Option 'project-name' is given more than once.", ErrorJson.decode(response.bodyAsText()))
    }
}
