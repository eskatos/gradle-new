package gradlenew.adaptertoolingapi

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateSpec
import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs
import gradlenew.substitution.Substitution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InitRunnerTest {

    private val init = InitRunner(GradleDistribution(Pinned.stable))

    @Test
    fun runsInitInAFreshDirectoryAndReturnsTheGeneratedFiles() {
        val files = init.run(listOf("--type", "java-application", "--dsl", "kotlin", "--project-name", "demo-app", "--package", "com.acme.demo", "--java-version", "17"))

        val paths = files.entries.map { it.path }
        assertTrue("settings.gradle.kts" in paths, paths.toString())
        assertTrue("app/src/main/java/com/acme/demo/App.java" in paths, paths.toString())
        assertTrue(paths.none { it.startsWith(".gradle/") }, paths.toString())
        assertTrue(files.entry("gradlew")!!.executable)
        assertTrue(!files.entry("gradlew.bat")!!.executable)
        assertTrue(files.entry("settings.gradle.kts")!!.content.decodeToString().contains("rootProject.name = \"demo-app\""))
        assertNotNull(files.entry("gradle/wrapper/gradle-wrapper.jar"))
    }

    @Test
    fun producesIdenticalFilesForIdenticalArguments() {
        val arguments = listOf("--type", "kotlin-library", "--dsl", "groovy", "--project-name", "lib", "--incubating")

        assertEquals(init.run(arguments), init.run(arguments))
    }

    @Test
    fun reportsInitsRejectionReason() {
        val packageRejection = assertFailsWith<InitRejected> {
            init.run(listOf("--type", "cpp-application", "--package", "org.example", "--project-name", "demo"))
        }
        assertEquals("Package name is not supported for 'cpp-application' build type.", packageRejection.reason)

        val frameworkRejection = assertFailsWith<InitRejected> {
            init.run(listOf("--type", "basic", "--test-framework", "junit", "--project-name", "demo"))
        }
        assertEquals("The requested test framework 'junit' is not supported for 'basic' build type. Supported frameworks:\n    - 'none'", frameworkRejection.reason)
    }

    @Test
    fun substitutedTemplatesEqualInitOutputForRealInputs() {
        val cases = listOf(
            TemplateSpec("cpp-library", "kotlin", "cpptest", "single-project", incubating = false, comments = true) to ProjectInputs("demo-app", null, null),
            TemplateSpec("swift-application", "groovy", "xctest", "single-project", incubating = false, comments = true) to ProjectInputs("my-cool-thing", null, null),
            TemplateSpec("kotlin-gradle-plugin", "groovy", "kotlintest", "single-project", incubating = true, comments = false) to ProjectInputs("my-cool-thing", "com.acme.tools", null),
            TemplateSpec("java-application", "kotlin", "junit-jupiter", "with-library-projects", incubating = false, comments = true) to ProjectInputs("demo.app", "com.acme.demo", 17),
            TemplateSpec("basic", "kotlin", null, "single-project", incubating = true, comments = true) to ProjectInputs("x_y-z2", null, null),
        )
        for ((spec, inputs) in cases) {
            val placeholders = ProjectInputs(
                Placeholders.PROJECT_NAME,
                Placeholders.PACKAGE.takeIf { inputs.packageName != null },
                Placeholders.JAVA_VERSION.takeIf { inputs.javaVersion != null },
            )
            val template = init.run(CatalogEntry(spec, placeholders).initArguments())
            val expected = init.run(CatalogEntry(spec, inputs).initArguments())

            assertEquals(emptyList(), expected.differencesFrom(Substitution.apply(template, inputs)), "substitution differs from init for ${spec.key}")
        }
    }
}
