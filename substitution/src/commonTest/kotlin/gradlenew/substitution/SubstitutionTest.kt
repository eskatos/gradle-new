package gradlenew.substitution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubstitutionTest {

    private val demo = ProjectInputs("demo-app", "com.acme.demo", 17)

    @Test
    fun placeholderFormsAreDistinctAndNoneContainsAnother() {
        val forms = Placeholders.forms()
        assertEquals(forms.size, forms.toSet().size)
        for (a in forms) for (b in forms) if (a != b) assertTrue(!a.contains(b), "'$a' contains '$b'")
        assertEquals(listOf("zqxproj-name", "ZqxprojName", "zqxproj_name", "zqxpkg.zqxsub", "zqxpkg/zqxsub", "JavaLanguageVersion.of(97)"), forms)
    }

    @Test
    fun substitutesAJvmApplication() {
        val template = FileSet.of(
            text("settings.gradle.kts", "rootProject.name = \"zqxproj-name\"\ninclude(\"app\")\n"),
            text("app/build.gradle.kts", "java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(97)\n    }\n}\napplication {\n    mainClass = \"zqxpkg.zqxsub.App\"\n}\n"),
            text("app/src/main/java/zqxpkg/zqxsub/App.java", "package zqxpkg.zqxsub;\n\npublic class App {}\n"),
            text("gradlew.bat", "@rem Gradle startup script\r\n@echo off\r\n"),
            text("gradlew", "#!/bin/sh\n", executable = true),
        )

        val result = Substitution.apply(template, demo)

        assertEquals(
            FileSet.of(
                text("settings.gradle.kts", "rootProject.name = \"demo-app\"\ninclude(\"app\")\n"),
                text("app/build.gradle.kts", "java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(17)\n    }\n}\napplication {\n    mainClass = \"com.acme.demo.App\"\n}\n"),
                text("app/src/main/java/com/acme/demo/App.java", "package com.acme.demo;\n\npublic class App {}\n"),
                text("gradlew.bat", "@rem Gradle startup script\r\n@echo off\r\n"),
                text("gradlew", "#!/bin/sh\n", executable = true),
            ),
            result,
        )
    }

    @Test
    fun substitutesTheCamelCaseFormInAGradlePlugin() {
        val template = FileSet.of(
            text("plugin/build.gradle", "gradlePlugin {\n    plugins {\n        greeting {\n            id = 'zqxpkg.zqxsub.greeting'\n            implementationClass = 'zqxpkg.zqxsub.ZqxprojNamePlugin'\n        }\n    }\n}\n"),
            text("plugin/src/main/kotlin/zqxpkg/zqxsub/ZqxprojNamePlugin.kt", "package zqxpkg.zqxsub\n\nclass ZqxprojNamePlugin\n"),
        )

        val result = Substitution.apply(template, ProjectInputs("my-cool-thing", "com.acme.tools", null))

        assertEquals(
            listOf("plugin/build.gradle", "plugin/src/main/kotlin/com/acme/tools/MyCoolThingPlugin.kt"),
            result.entries.map { it.path },
        )
        assertEquals("package com.acme.tools\n\nclass MyCoolThingPlugin\n", result.entries[1].content.decodeToString())
        assertTrue(result.entries[0].content.decodeToString().contains("implementationClass = 'com.acme.tools.MyCoolThingPlugin'"))
        assertTrue(result.entries[0].content.decodeToString().contains("id = 'com.acme.tools.greeting'"))
    }

    @Test
    fun substitutesTheLiteralAndNamespaceFormsInACppLibrary() {
        val template = FileSet.of(
            text("lib/src/main/public/zqxproj-name.h", "namespace zqxproj_name {\n    class Greeter {};\n}\n"),
            text("lib/src/main/cpp/hello.cpp", "#include \"zqxproj-name.h\"\n\nstd::string zqxproj_name::Greeter::greeting() {}\n"),
        )

        val result = Substitution.apply(template, ProjectInputs("demo-app", null, null))

        assertEquals(
            FileSet.of(
                text("lib/src/main/public/demo-app.h", "namespace demo_app {\n    class Greeter {};\n}\n"),
                text("lib/src/main/cpp/hello.cpp", "#include \"demo-app.h\"\n\nstd::string demo_app::Greeter::greeting() {}\n"),
            ),
            result,
        )
    }

    @Test
    fun substitutesTheModuleNameInASwiftApplication() {
        val template = FileSet.of(text("app/src/test/swift/AppTests.swift", "@testable import ZqxprojName\n"))

        val result = Substitution.apply(template, ProjectInputs("demo-app", null, null))

        assertEquals("@testable import DemoApp\n", result.entries.single().content.decodeToString())
    }

    @Test
    fun replacesInASinglePassSoRealValuesAreNeverSubstitutedAgain() {
        val template = FileSet.of(text("a.txt", "name zqxproj-name package zqxpkg.zqxsub"))

        val result = Substitution.apply(template, ProjectInputs("zqxpkg.zqxsub", "zqxproj_name.x", null))

        assertEquals("name zqxpkg.zqxsub package zqxproj_name.x", result.entries.single().content.decodeToString())
    }

    @Test
    fun leavesBinaryEntriesUntouched() {
        val binary = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00) + "zqxproj-name".encodeToByteArray() + byteArrayOf(0xff.toByte(), 0xfe.toByte())
        val template = FileSet.of(FileEntry("gradle/wrapper/gradle-wrapper.jar", binary, false))

        val result = Substitution.apply(template, demo)

        assertTrue(result.entries.single().content.contentEquals(binary))
    }

    @Test
    fun failsWhenATemplateNeedsAnInputThatIsAbsent() {
        val withPackage = FileSet.of(text("App.java", "package zqxpkg.zqxsub;"))
        val withJavaVersion = FileSet.of(text("build.gradle.kts", "languageVersion = JavaLanguageVersion.of(97)"))

        assertFailsWith<IllegalArgumentException> { Substitution.apply(withPackage, ProjectInputs("demo", null, 17)) }
        assertFailsWith<IllegalArgumentException> { Substitution.apply(withJavaVersion, ProjectInputs("demo", "a.b", null)) }
    }

    @Test
    fun placeholderInputsSubstituteToThemselves() {
        val template = FileSet.of(text("settings.gradle.kts", "rootProject.name = \"zqxproj-name\""), text("App.java", "package zqxpkg.zqxsub;"))

        assertEquals(template, Substitution.apply(template, Placeholders.inputs))
    }

    private fun text(path: String, content: String, executable: Boolean = false) =
        FileEntry(path, content.encodeToByteArray(), executable)
}
