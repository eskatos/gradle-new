package gradlenew.adaptertoolingapi

import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderCompilerTest {

    @Test
    fun compilesTheBuilderMiniBuildWithTheVersionUnderTest() {
        for (version in Pinned.versions) {
            val workDir = Files.createTempDirectory("builder-compile").toFile()

            val jars = BuilderCompiler.compile(Pinned.distribution(version), Pinned.builderSources, Pinned.versionCatalog, workDir)

            assertTrue(jars.plugin.isFile, jars.plugin.path)
            assertTrue(jars.api.isFile, jars.api.path)
            JarFile(jars.plugin).use { assertTrue(it.getEntry("gradlenew/builder/InitModelPlugin.class") != null) }
            JarFile(jars.api).use { assertTrue(it.getEntry("gradlenew/builder/api/InitModel.class") != null) }
            assertEquals(false, workDir.resolve("builder/build/libs").exists())
        }
    }
}
