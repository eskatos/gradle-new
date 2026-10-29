package gradlenew.adaptertoolingapi

import gradlenew.domain.generation.CatalogEntry
import gradlenew.domain.generation.TemplateCatalog
import gradlenew.domain.model.BuildType
import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs
import gradlenew.substitution.Substitution
import java.nio.file.Files
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
    fun reportsInitsOwnRejectionReason() {
        val arguments = listOf("--type", "no-such-type", "--project-name", "demo")

        val rejection = assertFailsWith<InitRejected> { init.run(arguments) }
        val raw = assertFailsWith<GradleBuildFailed> {
            GradleDistribution(Pinned.stable).runTasks(Files.createTempDirectory("raw-init").toFile(), listOf("init", "--use-defaults", "--offline", "--quiet") + arguments)
        }

        assertTrue(rejection.reason.contains("no-such-type"), rejection.reason)
        assertTrue(rejection.reason.lines().all { it.isNotBlank() && it in raw.output }, "${rejection.reason}\n---\n${raw.output}")
    }

    @Test
    fun substitutedTemplatesEqualInitOutputForRealInputs() {
        val model = InitHelp.model(GradleDistribution(Pinned.stable))
        val catalog = TemplateCatalog.of(model)
        val cases = listOf<Pair<(BuildType) -> Boolean, ProjectInputs>>(
            { type: BuildType -> type.language == "cpp" } to ProjectInputs("demo-app", null, null),
            { type: BuildType -> type.language == "swift" } to ProjectInputs("my-cool-thing", null, null),
            { type: BuildType -> type.componentType == "gradle-plugin" && type.supportsPackage } to ProjectInputs("my-cool-thing", "com.acme.tools", null),
            { type: BuildType -> type.supportsJavaVersion && type.modularization.values.size > 1 } to ProjectInputs("demo.app", "com.acme.demo", 17),
            { type: BuildType -> type.language == null } to ProjectInputs("x_y-z2", null, null),
        )
        var compared = 0
        for ((matches, requested) in cases) {
            val type = model.buildTypes.firstOrNull(matches) ?: continue
            val spec = catalog.map { it.spec }.filter { it.type == type.id }.maxBy { it.modularization != type.modularization.default }
            val inputs = ProjectInputs(
                requested.projectName,
                requested.packageName.takeIf { type.supportsPackage },
                requested.javaVersion.takeIf { type.supportsJavaVersion },
            )
            val placeholders = ProjectInputs(
                Placeholders.PROJECT_NAME,
                Placeholders.PACKAGE.takeIf { inputs.packageName != null },
                Placeholders.JAVA_VERSION.takeIf { inputs.javaVersion != null },
            )
            val template = init.run(CatalogEntry(spec, placeholders).initArguments())
            val expected = init.run(CatalogEntry(spec, inputs).initArguments())

            assertEquals(emptyList(), expected.differencesFrom(Substitution.apply(template, inputs)), "substitution differs from init for ${spec.key}")
            compared++
        }
        assertTrue(compared >= 3, "only $compared build types matched the cases")
    }
}
