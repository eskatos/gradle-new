package gradlenew.publisher

import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.OfferedVersions
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IndexCommandTest {

    private val output = Files.createTempDirectory("index").toFile().resolve("site-index")

    @Test
    fun writesTheVersionsIndexFromTheOfferedVersions() {
        IndexCommand.run(listOf("--stable", "9.8.2", "--rc", "9.9.0-rc-1", "--previous", "8.14.6", "--output", output.path))

        assertEquals(OfferedVersions("9.8.2", "9.9.0-rc-1", "8.14.6"), VersionsJson.decode(output.resolve("versions.json").readText()))
        assertEquals(listOf("versions.json"), output.list()!!.toList())
    }

    @Test
    fun anEmptyOrMissingRcMeansNone() {
        IndexCommand.run(listOf("--stable", "9.8.2", "--rc", "", "--previous", "8.14.6", "--output", output.path))
        assertEquals(OfferedVersions("9.8.2", null, "8.14.6"), VersionsJson.decode(output.resolve("versions.json").readText()))
        IndexCommand.run(listOf("--stable", "9.8.2", "--previous", "8.14.6", "--output", output.path))
        assertEquals(OfferedVersions("9.8.2", null, "8.14.6"), VersionsJson.decode(output.resolve("versions.json").readText()))
    }

    @Test
    fun requiresTheStableAndPreviousMajorVersionsAndTheOutput() {
        assertFailsWith<IllegalArgumentException> { IndexCommand.run(listOf("--previous", "8.14.6", "--output", output.path)) }
        assertFailsWith<IllegalArgumentException> { IndexCommand.run(listOf("--stable", "9.8.2", "--output", output.path)) }
        assertFailsWith<IllegalArgumentException> { IndexCommand.run(listOf("--stable", "9.8.2", "--previous", "8.14.6")) }
    }
}
