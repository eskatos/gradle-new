package gradlenew.adaptertoolingapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Gradle982FactsTest {

    private val gradle = GradleDistribution("9.8.2")

    @Test
    fun theModelOfGradle982() {
        val model = InitHelp.model(gradle)

        assertEquals("9.8.2", model.gradleVersion)
        assertEquals(16, model.buildTypes.size, model.buildTypes.map { it.id }.toString())
        assertFalse(model.buildTypes.any { it.id == "pom" })
        assertEquals(listOf("kotlin", "groovy"), model.dsls.map { it.id })
        assertEquals(7, model.javaVersion.minimum)
        assertEquals(21, model.javaVersion.default)
        assertEquals(31, model.javaVersion.maximum)
        assertEquals("org.example", model.packageName.default)
        assertEquals("Set the type of project to generate.", model.option("type")?.description)
        assertEquals("Split functionality across multiple subprojects?", model.option("split-project")?.description)
        assertEquals("Disables option --split-project.", model.option("split-project")?.negatedDescription)

        val javaApplication = model.buildType("java-application")
        assertEquals("application", javaApplication.componentType)
        assertEquals("java", javaApplication.language)
        assertEquals(listOf("kotlin", "groovy"), javaApplication.dsls.values)
        assertEquals("kotlin", javaApplication.dsls.default)
        assertEquals(listOf("single-project", "with-library-projects"), javaApplication.modularization.values)
        assertTrue("junit-jupiter" in javaApplication.testFrameworksFor("single-project").values)
        assertTrue(javaApplication.supportsPackage && javaApplication.supportsJavaVersion)

        val basic = model.buildType("basic")
        assertNull(basic.language)
        assertEquals(emptyList(), basic.testFrameworksFor("single-project").values)
        assertFalse(basic.supportsPackage)
        assertEquals("cpp", model.buildType("cpp-application").language)
        assertFalse(model.buildType("cpp-application").supportsPackage)
    }

    @Test
    fun theRejectionWordingOfGradle982() {
        val init = InitRunner(gradle)

        val packageRejection = assertFailsWith<InitRejected> {
            init.run(listOf("--type", "cpp-application", "--package", "org.example", "--project-name", "demo"))
        }
        assertEquals("Package name is not supported for 'cpp-application' build type.", packageRejection.reason)

        val frameworkRejection = assertFailsWith<InitRejected> {
            init.run(listOf("--type", "basic", "--test-framework", "junit", "--project-name", "demo"))
        }
        assertEquals("The requested test framework 'junit' is not supported for 'basic' build type. Supported frameworks:\n    - 'none'", frameworkRejection.reason)
    }
}
