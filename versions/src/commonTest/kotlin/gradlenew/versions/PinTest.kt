package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PinTest {

    private val stable = PinnedVersion("9.8.2", 25, Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982))
    private val previous = PinnedVersion("8.14.6", 21)
    private val rc = PinnedVersion("9.9.0-rc-1", 25, Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC))

    @Test
    fun pinsAStableAPreviousMajorAndAnOptionalReleaseCandidate() {
        val pin = Pin(stable, previous, rc)
        assertEquals(stable, pin[Slot.STABLE])
        assertEquals(previous, pin[Slot.PREVIOUS])
        assertEquals(rc, pin[Slot.RC])
        assertNull(Pin(stable, previous)[Slot.RC])
        assertEquals(listOf(stable, rc, previous), pin.versions)
    }

    @Test
    fun eachPinnedVersionCarriesTheJavaThatRunsItAndOptionallyItsChecksums() {
        assertEquals(25, stable.java)
        assertEquals(21, previous.java)
        assertNull(previous.checksums)
        assertFailsWith<IllegalArgumentException> { PinnedVersion("9.8.2", 0) }
    }

    @Test
    fun theStableIsAFinalReleaseAndTheReleaseCandidateACandidate() {
        assertFailsWith<IllegalArgumentException> { Pin(PinnedVersion("9.9.0-rc-1", 25), previous) }
        assertFailsWith<IllegalArgumentException> { Pin(PinnedVersion("nine", 25), previous) }
        assertFailsWith<IllegalArgumentException> { Pin(stable, previous, PinnedVersion("9.9.0", 25)) }
    }

    @Test
    fun thePreviousMajorIsAFinalReleaseOneMajorBelowTheStable() {
        assertFailsWith<IllegalArgumentException> { Pin(stable, PinnedVersion("9.8.1", 25)) }
        assertFailsWith<IllegalArgumentException> { Pin(stable, PinnedVersion("7.6.4", 17)) }
        assertFailsWith<IllegalArgumentException> { Pin(stable, PinnedVersion("8.14.7-rc-1", 21)) }
        assertEquals(PinnedVersion("9.9.1", 25), Pin(PinnedVersion("10.0.0", 25), PinnedVersion("9.9.1", 25)).previous)
    }

    @Test
    fun replacesOneSlot() {
        val pin = Pin(stable, previous)
        assertEquals(Pin(stable, previous, rc), pin.with(Slot.RC, rc))
        assertEquals(Pin(stable, PinnedVersion("8.14.7", 21)), pin.with(Slot.PREVIOUS, PinnedVersion("8.14.7", 21)))
        assertEquals(pin, Pin(stable, previous, rc).with(Slot.RC, null))
        assertFailsWith<IllegalArgumentException> { pin.with(Slot.STABLE, null) }
    }

    @Test
    fun eachSlotHasAnIdentifier() {
        assertEquals(listOf("stable", "rc", "previous"), Slot.entries.map { it.id })
        assertEquals(Slot.PREVIOUS, Slot.of("previous"))
    }
}
