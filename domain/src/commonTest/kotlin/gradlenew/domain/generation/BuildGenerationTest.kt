package gradlenew.domain.generation

import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildGenerationTest {

    private val requested = mutableListOf<Pair<String, String>>()

    private val template = FileSet.of(
        text("settings.gradle.kts", "rootProject.name = \"zqxproj-name\""),
        text("app/src/main/java/zqxpkg/zqxsub/App.java", "package zqxpkg.zqxsub;"),
        text("app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(97)"),
        FileEntry("gradlew", "#!/bin/sh".encodeToByteArray(), true),
    )

    private val generation = BuildGeneration(OfferedVersions("9.8.2", null, "8.14.6"), { gradle982Model }) { version, key ->
        requested += version to key
        template.takeIf { key.startsWith("java-application.") }
    }

    @Test
    fun substitutesTheSelectedTemplateUnderTheProjectDirectory() {
        val result = generation.generate(mapOf("type" to "java-application", "project-name" to "demo-app", "package" to "com.acme.demo", "java-version" to "17"))

        val generated = assertIs<GenerationResult.Generated>(result)
        assertEquals(listOf("9.8.2" to "java-application.kotlin.junit-jupiter.single-project.no-incubating.comments"), requested)
        assertEquals(
            FileSet.of(
                text("demo-app/settings.gradle.kts", "rootProject.name = \"demo-app\""),
                text("demo-app/app/src/main/java/com/acme/demo/App.java", "package com.acme.demo;"),
                text("demo-app/app/build.gradle.kts", "languageVersion = JavaLanguageVersion.of(17)"),
                FileEntry("demo-app/gradlew", "#!/bin/sh".encodeToByteArray(), true),
            ),
            generated.files,
        )
        assertEquals("demo-app.zip", generated.filename)
        assertFalse(generated.immutable)
    }

    @Test
    fun marksResultsForAConcreteVersionAsImmutable() {
        val result = generation.generate(mapOf("version" to "9.8.2", "type" to "java-application", "project-name" to "demo"))

        assertTrue(assertIs<GenerationResult.Generated>(result).immutable)
    }

    @Test
    fun rejectsRepeatedOptions() {
        val result = generation.generate(listOf("type" to "java-application", "project-name" to "a", "project-name" to "b"))

        assertEquals(GenerationResult.Rejected("Option 'project-name' is given more than once.", "project-name"), result)
    }

    @Test
    fun acceptsParametersAsAList() {
        val result = generation.generate(listOf("type" to "java-application", "project-name" to "demo"))

        assertEquals("demo.zip", assertIs<GenerationResult.Generated>(result).filename)
    }

    @Test
    fun passesRejectionsThroughWithoutReadingATemplate() {
        val result = generation.generate(mapOf("type" to "cpp-application", "project-name" to "demo", "package" to "org.example"))

        assertEquals(GenerationResult.Rejected("Package name is not supported for 'cpp-application' build type.", "package"), result)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun failsWhenThePublishedTemplateIsMissing() {
        val failure = assertFailsWith<IllegalStateException> { generation.generate(mapOf("type" to "basic", "project-name" to "demo")) }

        assertEquals("No template 'basic.kotlin.none.single-project.no-incubating.comments' was published for Gradle 9.8.2", failure.message)
    }

    @Test
    fun selectsAnEnumeratedTemplateByJavaVersionAndKeepsItsJavaVersion() {
        val enumeratedTemplate = FileSet.of(
            text("settings.gradle", "rootProject.name = 'zqxproj-name'"),
            text("lib/build.gradle", "languageVersion = JavaLanguageVersion.of(17)\n// targets Java 17"),
        )
        val keys = mutableListOf<String>()
        val enumerated = BuildGeneration(OfferedVersions("9.8.2", null, "8.14.6"), { gradle982Model }, indexes = { TemplateIndex(setOf("groovy-library")) }) { version, key ->
            keys += key
            enumeratedTemplate.takeIf { version == "9.8.2" }
        }

        val result = enumerated.generate(mapOf("type" to "groovy-library", "project-name" to "demo", "java-version" to "17"))

        assertEquals(listOf("groovy-library.groovy.spock.single-project.no-incubating.comments.java17"), keys)
        assertEquals(
            FileSet.of(
                text("demo/settings.gradle", "rootProject.name = 'demo'"),
                text("demo/lib/build.gradle", "languageVersion = JavaLanguageVersion.of(17)\n// targets Java 17"),
            ),
            assertIs<GenerationResult.Generated>(result).files,
        )
    }

    private fun text(path: String, content: String) = FileEntry(path, content.encodeToByteArray(), false)
}
