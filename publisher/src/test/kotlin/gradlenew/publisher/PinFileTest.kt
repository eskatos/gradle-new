package gradlenew.publisher

import gradlenew.versions.Checksums
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
    fun readsTheChecksumsOfEachSlot() {
        file.writeText("stable=9.8.2\nstable.distribution-sha256=${"1".repeat(64)}\nstable.wrapper-sha256=${"2".repeat(64)}\nrc=9.9.0-rc-1\nrc.distribution-sha256=${"3".repeat(64)}\nrc.wrapper-sha256=${"4".repeat(64)}\n")
        assertEquals(Pin("9.8.2", "9.9.0-rc-1", Checksums("1".repeat(64), "2".repeat(64)), Checksums("3".repeat(64), "4".repeat(64))), PinFile.read(file))
        file.writeText("stable=9.8.2\nstable.distribution-sha256=\nstable.wrapper-sha256=\nrc=\nrc.distribution-sha256=\nrc.wrapper-sha256=\n")
        assertEquals(Pin("9.8.2", null), PinFile.read(file))
    }

    @Test
    fun writesSixLinesInAFixedOrderWithoutATimestamp() {
        PinFile.write(file, Pin("9.9.0", "9.10.0-rc-1", Checksums("1".repeat(64), "2".repeat(64)), Checksums("3".repeat(64), "4".repeat(64))))
        assertEquals(
            "stable=9.9.0\nstable.distribution-sha256=${"1".repeat(64)}\nstable.wrapper-sha256=${"2".repeat(64)}\n" +
                "rc=9.10.0-rc-1\nrc.distribution-sha256=${"3".repeat(64)}\nrc.wrapper-sha256=${"4".repeat(64)}\n",
            file.readText(),
        )
        PinFile.write(file, Pin("9.9.0", null))
        assertEquals("stable=9.9.0\nstable.distribution-sha256=\nstable.wrapper-sha256=\nrc=\nrc.distribution-sha256=\nrc.wrapper-sha256=\n", file.readText())
    }

    @Test
    fun rejectsHalfPinnedChecksumsAndMalformedDigests() {
        for (content in listOf(
            "stable=9.8.2\nstable.distribution-sha256=${"1".repeat(64)}\n",
            "stable=9.8.2\nstable.distribution-sha256=abc\nstable.wrapper-sha256=${"2".repeat(64)}\n",
            "stable=9.8.2\nrc=\nrc.distribution-sha256=${"3".repeat(64)}\nrc.wrapper-sha256=${"4".repeat(64)}\n",
        )) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }

    @Test
    fun rejectsAMissingStableUnknownKeysAndMalformedLines() {
        for (content in listOf("rc=\n", "stable=9.8.2\nlts=8.14\n", "stable 9.8.2\n", "stable=9.9.0-rc-1\n")) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }
}
