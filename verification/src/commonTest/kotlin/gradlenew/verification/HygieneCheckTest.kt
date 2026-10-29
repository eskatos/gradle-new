package gradlenew.verification

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HygieneCheckTest {

    private val input = ProjectInputs("demo-app", "com.acme.demo", 17)

    @Test
    fun acceptsATemplateWhosePlaceholdersAreAllKnownForms() {
        val template = fakeInit(Placeholders.inputs, "java-application", listOf(text("README.md", "ZqxprojName, zqxproj_name and ZQXPROJ_NAME in zqxpkg/zqxsub")))

        assertEquals(emptyList(), HygieneCheck.inspect("java-application.kotlin", template, input))
    }

    @Test
    fun namesTheFileWhereAnUnknownFormRemains() {
        val template = fakeInit(Placeholders.inputs, "java-application", listOf(text("PROJECT.txt", "zqxproj name\n")))

        val failures = HygieneCheck.inspect("java-application.kotlin", template, input)

        assertEquals(listOf(Failure("java-application.kotlin", "PROJECT.txt: placeholder 'zqxproj' remains after substitution")), failures)
    }

    @Test
    fun findsUnknownFormsInPathsAndInAnyCase() {
        val template = FileSet.of(text("docs/Zqxpkg-notes.txt", "notes"), text("a.txt", "ZQXSUB"))

        val failures = HygieneCheck.inspect("basic.kotlin", template, ProjectInputs("demo", null, null)).map { it.message }

        assertEquals(listOf("a.txt: placeholder 'zqxsub' remains after substitution", "docs/Zqxpkg-notes.txt: placeholder 'zqxpkg' remains after substitution"), failures)
    }

    @Test
    fun refusesPlaceholderBytesInBinaryEntries() {
        val jar = byteArrayOf(0x50, 0x4b, 0x00) + "ZqxprojName.class".encodeToByteArray()
        val template = FileSet.of(FileEntry("lib/plugin.jar", jar, false), text("settings.gradle.kts", "rootProject.name = \"zqxproj-name\""))

        val failures = HygieneCheck.inspect("kotlin-gradle-plugin.kotlin", template, input)

        assertEquals(listOf(Failure("kotlin-gradle-plugin.kotlin", "lib/plugin.jar: binary entry contains placeholder 'zqxproj'")), failures)
    }

    @Test
    fun acceptsABinaryEntryMovedUnderTheRealPackage() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x00)
        val template = FileSet.of(FileEntry("src/main/resources/zqxpkg/zqxsub/logo.png", image, false))

        assertEquals(emptyList(), HygieneCheck.inspect("java-application.kotlin", template, input))
    }

    @Test
    fun reportsAPlaceholderForAnInputTheTypeDoesNotTake() {
        val template = FileSet.of(text("src/main/cpp/app.cpp", "namespace zqxpkg.zqxsub {}"))

        val failures = HygieneCheck.inspect("cpp-application.kotlin", template, ProjectInputs("demo", null, null))

        assertEquals(1, failures.size)
        assertTrue(failures.single().message.contains("package placeholder"), failures.toString())
    }
}
