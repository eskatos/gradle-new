package gradlenew.adaptertoolingapi

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InitModelRequestTest {

    @Test
    fun requestsTheInitOptionsModelFromTheVersionUnderTest() {
        for (version in Pinned.versions) {
            val workDir = Files.createTempDirectory("model-request").toFile()
            val gradle = GradleDistribution(version)
            val jars = BuilderCompiler.compile(gradle, Pinned.builderSources, Pinned.versionCatalog, workDir)

            val model = InitModelRequest.request(gradle, jars, workDir)

            assertEquals(version, model.gradleVersion)
            assertTrue(model.buildTypes.size >= 16, model.buildTypes.map { it.id }.toString())
            assertFalse(model.buildTypes.any { it.id == "pom" })

            val javaApplication = model.buildType("java-application")
            assertEquals("application", javaApplication.componentType)
            assertEquals("java", javaApplication.language)
            assertEquals(listOf("kotlin", "groovy"), javaApplication.dsls.values)
            assertEquals("kotlin", javaApplication.dsls.default)
            assertEquals(listOf("single-project", "with-library-projects"), javaApplication.modularization.values)
            assertTrue("junit-jupiter" in javaApplication.testFrameworksFor("single-project").values)
            assertTrue(javaApplication.supportsPackage)
            assertTrue(javaApplication.supportsJavaVersion)

            val basic = model.buildType("basic")
            assertNull(basic.language)
            assertEquals(emptyList(), basic.testFrameworksFor("single-project").values)
            assertFalse(basic.supportsPackage)

            assertFalse(model.buildType("cpp-application").supportsPackage)
            assertEquals("cpp", model.buildType("cpp-application").language)
            assertEquals(7, model.javaVersion.minimum)
            assertEquals(21, model.javaVersion.default)
            assertEquals("org.example", model.packageName.default)
            assertEquals(listOf("kotlin", "groovy"), model.dsls.map { it.id })
        }
    }
}
