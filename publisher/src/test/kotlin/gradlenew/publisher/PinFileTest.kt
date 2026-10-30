package gradlenew.publisher

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.PinnedVersion
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PinFileTest {

    private val file = Files.createTempDirectory("pin").resolve("offered-gradle-versions.properties").toFile()
    private val stable = PinnedVersion("9.8.2", 25, Checksums("1".repeat(64), "2".repeat(64)))
    private val rc = PinnedVersion("9.9.0-rc-1", 25, Checksums("3".repeat(64), "4".repeat(64)))
    private val previous = PinnedVersion("8.14.6", 21, Checksums("5".repeat(64), "6".repeat(64)))
    private val fullText =
        "stable=9.8.2\nstable.distribution-sha256=${"1".repeat(64)}\nstable.wrapper-sha256=${"2".repeat(64)}\nstable.daemon-java=25\n" +
            "rc=9.9.0-rc-1\nrc.distribution-sha256=${"3".repeat(64)}\nrc.wrapper-sha256=${"4".repeat(64)}\nrc.daemon-java=25\n" +
            "previous=8.14.6\nprevious.distribution-sha256=${"5".repeat(64)}\nprevious.wrapper-sha256=${"6".repeat(64)}\nprevious.daemon-java=21\n"

    @Test
    fun readsEverySlotWithItsJavaAndChecksums() {
        file.writeText(fullText)
        assertEquals(Pin(stable, previous, rc), PinFile.read(file))
    }

    @Test
    fun readsAnEmptyOrMissingRcAndMissingChecksums() {
        file.writeText("# offered versions\nstable = 9.8.2\nstable.daemon-java = 25\n\nrc =\nprevious = 8.14.6\nprevious.daemon-java = 21\n")
        assertEquals(Pin(PinnedVersion("9.8.2", 25), PinnedVersion("8.14.6", 21)), PinFile.read(file))
    }

    @Test
    fun writesTwelveLinesInAFixedOrderWithoutATimestamp() {
        PinFile.write(file, Pin(stable, previous, rc))
        assertEquals(fullText, file.readText())
        PinFile.write(file, Pin(PinnedVersion("9.8.2", 25), PinnedVersion("8.14.6", 21)))
        assertEquals(
            "stable=9.8.2\nstable.distribution-sha256=\nstable.wrapper-sha256=\nstable.daemon-java=25\n" +
                "rc=\nrc.distribution-sha256=\nrc.wrapper-sha256=\nrc.daemon-java=\n" +
                "previous=8.14.6\nprevious.distribution-sha256=\nprevious.wrapper-sha256=\nprevious.daemon-java=21\n",
            file.readText(),
        )
        assertEquals(Pin(PinnedVersion("9.8.2", 25), PinnedVersion("8.14.6", 21)), PinFile.read(file))
    }

    @Test
    fun rejectsHalfPinnedChecksumsAndMalformedDigests() {
        val versions = "stable.daemon-java=25\nprevious=8.14.6\nprevious.daemon-java=21\n"
        for (content in listOf(
            "stable=9.8.2\nstable.distribution-sha256=${"1".repeat(64)}\n$versions",
            "stable=9.8.2\nstable.distribution-sha256=abc\nstable.wrapper-sha256=${"2".repeat(64)}\n$versions",
            "stable=9.8.2\nrc=\nrc.distribution-sha256=${"3".repeat(64)}\nrc.wrapper-sha256=${"4".repeat(64)}\n$versions",
        )) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }

    @Test
    fun requiresTheStableThePreviousMajorAndTheJavaOfEveryPinnedVersion() {
        for (content in listOf(
            "rc=\nprevious=8.14.6\nprevious.daemon-java=21\n",
            "stable=9.8.2\nstable.daemon-java=25\n",
            "stable=9.8.2\nprevious=8.14.6\nprevious.daemon-java=21\n",
            "stable=9.8.2\nstable.daemon-java=25\nprevious=8.14.6\n",
            "stable=9.8.2\nstable.daemon-java=25\nrc=9.9.0-rc-1\nprevious=8.14.6\nprevious.daemon-java=21\n",
            "stable=9.8.2\nstable.daemon-java=twenty-five\nprevious=8.14.6\nprevious.daemon-java=21\n",
            "stable=9.8.2\nstable.daemon-java=25\nrc=\nrc.daemon-java=25\nprevious=8.14.6\nprevious.daemon-java=21\n",
            "stable=9.8.2\nstable.daemon-java=25\nprevious=9.8.1\nprevious.daemon-java=25\n",
        )) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }

    @Test
    fun rejectsUnknownKeysAndMalformedLines() {
        for (content in listOf("stable=9.8.2\nlts=8.14\n", "stable 9.8.2\n", "stable=9.9.0-rc-1\nstable.daemon-java=25\nprevious=8.14.6\nprevious.daemon-java=21\n")) {
            file.writeText(content)
            assertFailsWith<IllegalArgumentException>(content) { PinFile.read(file) }
        }
    }
}
