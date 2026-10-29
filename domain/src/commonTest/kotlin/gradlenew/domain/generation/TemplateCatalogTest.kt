package gradlenew.domain.generation

import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateCatalogTest {

    private val catalog = TemplateCatalog.of(gradle982Model)

    @Test
    fun enumeratesEveryCombinationOfTheEnumeratedOptions() {
        assertEquals(224, catalog.size)
        assertEquals(56 * 4, catalog.size)
        assertEquals(8, catalog.count { it.spec.type == "java-library" && !it.spec.incubating && it.spec.comments })
        assertEquals(2, catalog.count { it.spec.type == "basic" && !it.spec.incubating && it.spec.comments })
    }

    @Test
    fun keysAreUniqueAndStable() {
        assertEquals(catalog.size, catalog.map { it.spec.key }.toSet().size)
        assertEquals(
            "java-application.kotlin.junit-jupiter.single-project.no-incubating.comments",
            TemplateSpec("java-application", "kotlin", "junit-jupiter", "single-project", incubating = false, comments = true).key,
        )
        assertEquals(
            "basic.groovy.none.single-project.incubating.no-comments",
            TemplateSpec("basic", "groovy", null, "single-project", incubating = true, comments = false).key,
        )
    }

    @Test
    fun followsTheTestFrameworksOfEachModularizationOption() {
        val splitJava = catalog.filter { it.spec.type == "java-application" && it.spec.modularization == "with-library-projects" }
        assertEquals(setOf("junit-jupiter"), splitJava.map { it.spec.testFramework }.toSet())
        val singleJava = catalog.filter { it.spec.type == "java-application" && it.spec.modularization == "single-project" }
        assertEquals(setOf("junit", "testng", "spock", "junit-jupiter"), singleJava.map { it.spec.testFramework }.toSet())
        assertEquals(setOf(null), catalog.filter { it.spec.type == "basic" }.map { it.spec.testFramework }.toSet())
    }

    @Test
    fun usesOnlyThePlaceholdersATypeSupports() {
        assertEquals(Placeholders.inputs, entry("java-application").placeholders)
        assertEquals(ProjectInputs(Placeholders.PROJECT_NAME, Placeholders.PACKAGE, null), entry("kotlin-gradle-plugin").placeholders)
        assertEquals(ProjectInputs(Placeholders.PROJECT_NAME, null, null), entry("cpp-library").placeholders)
        assertEquals(ProjectInputs(Placeholders.PROJECT_NAME, null, null), entry("basic").placeholders)
    }

    @Test
    fun producesInitArgumentsForAnEntry() {
        val split = catalog.first { it.spec.key == "kotlin-application.groovy.junit-jupiter.with-library-projects.incubating.no-comments" }
        assertEquals(
            listOf(
                "--type", "kotlin-application", "--dsl", "groovy", "--test-framework", "junit-jupiter",
                "--split-project", "--incubating", "--no-comments",
                "--project-name", "zqxproj-name", "--package", "zqxpkg.zqxsub", "--java-version", "97",
            ),
            split.initArguments(),
        )
        val basic = catalog.first { it.spec.key == "basic.kotlin.none.single-project.no-incubating.comments" }
        assertEquals(
            listOf("--type", "basic", "--dsl", "kotlin", "--no-split-project", "--no-incubating", "--comments", "--project-name", "zqxproj-name"),
            basic.initArguments(),
        )
    }

    @Test
    fun everyKeyHasSixSegments() {
        assertTrue(catalog.all { it.spec.key.split('.').size == 6 }, catalog.map { it.spec.key }.toString())
    }

    private fun entry(type: String) = catalog.first { it.spec.type == type }
}
