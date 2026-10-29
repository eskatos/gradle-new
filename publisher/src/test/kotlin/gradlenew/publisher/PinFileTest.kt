package gradlenew.publisher

import gradlenew.versions.Pin
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PinFileTest {

    private val file = Files.createTempDirectory("pin").resolve("offered-gradle-versions.properties").toFile()

    @Test
    fun readsStableAndAnEmptyOrPresentRc() {
        file.writeText("stable=9.8.2\nrc=\n")
        assertEquals(Pin("9.8.2", null), PinFile.read(file))
        file.writeText("# offered versions\nstable = 9.8.2\n\nrc = 9.9.0-rc-1\n")
        assertEquals(Pin("9.8.2", "9.9.0-rc-1"), PinFile.read(file))
        file.writeText("stable=9.8.2\n")
        assertEquals(Pin("9.8.2", null), PinFile.read(file))
    }

    @Test
    fun writesExactlyTwoLinesWithoutATimestamp() {
        PinFile.write(file, Pin("9.9.0", "9.10.0-rc-1"))
        assertEquals("stable=9.9.0\nrc=9.10.0-rc-1\n", file.readText())
        PinFile.write(file, Pin("9.9.0", null))
        assertEquals("stable=9.9.0\nrc=\n", file.readText())
    }

    @Test
    fun rejectsAMissingStableUnknownKeysAndMalformedLines() {
        for (content in listOf("rc=\n", "stable=9.8.2\nlts=8.14\n", "stable 9.8.2\n", "stable=9.9.0-rc-1\n")) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }
}
