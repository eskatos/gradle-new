package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfferingPolicyTest {

    private val pinned = Pin("9.8.2", null)

    @Test
    fun keepsThePinWhenTheFeedsMatchIt() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.unchanged, pinned))
    }

    @Test
    fun offersAnActiveReleaseCandidateAboveTheStableVersion() {
        assertEquals(Pin("9.8.2", "9.9.0-rc-1"), OfferingPolicy.offer(RecordedFeeds.activeRc, pinned))
    }

    @Test
    fun retiresTheReleaseCandidateWhenItsFinalShips() {
        assertEquals(Pin("9.9.0", null), OfferingPolicy.offer(RecordedFeeds.finalRetiresRc, Pin("9.8.2", "9.9.0-rc-2")))
    }

    @Test
    fun offersANewStableAndANewerReleaseCandidateTogether() {
        assertEquals(Pin("9.9.0", "9.10.0-rc-1"), OfferingPolicy.offer(RecordedFeeds.newStableAndRc, pinned))
    }

    @Test
    fun keepsThePinnedStableWhenTheNewOneIsBroken() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.brokenStable, pinned))
    }

    @Test
    fun clearsAPinnedReleaseCandidateThatIsNoLongerActive() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.inactiveRc, Pin("9.8.2", "9.9.0-rc-1")))
    }

    @Test
    fun ignoresMilestonesAndNightlies() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.milestoneAndNightly, pinned))
    }

    @Test
    fun neverOffersALowerStableThanThePinnedOne() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.olderStable, pinned))
    }

    @Test
    fun dropsAReleaseCandidateWhoseBaseIsNotAboveTheOfferedStable() {
        val stale = Feeds(FeedEntry("9.9.0"), FeedEntry("9.9.0-rc-3", activeRc = true, rcFor = "9.9.0"))
        assertEquals(Pin("9.9.0", null), OfferingPolicy.offer(stale, pinned))
    }

    @Test
    fun ignoresABrokenReleaseCandidateAndOneThatDisagreesWithItsBase() {
        assertEquals(pinned, OfferingPolicy.offer(Feeds(FeedEntry("9.8.2"), FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0", broken = true)), pinned))
        assertEquals(pinned, OfferingPolicy.offer(Feeds(FeedEntry("9.8.2"), FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.10.0")), pinned))
    }

    @Test
    fun keepsThePinnedStableWhenTheCurrentFeedIsEmpty() {
        assertEquals(pinned, OfferingPolicy.offer(Feeds(null, null), pinned))
    }

    @Test
    fun pinsAFinalStableAndAnOptionalReleaseCandidate() {
        assertFailsWith<IllegalArgumentException> { Pin("9.9.0-rc-1", null) }
        assertFailsWith<IllegalArgumentException> { Pin("9.8.2", "9.9.0") }
        assertFailsWith<IllegalArgumentException> { Pin("nine", null) }
    }

    @Test
    fun anOfferedVersionCarriesTheFeedsChecksumsAndAKeptOneKeepsThePinnedChecksums() {
        val pinned = Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982)
        val pin = Pin("9.8.2", null, pinned)
        val feeds = Feeds(
            FeedEntry("9.9.0", checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990),
            FeedEntry("9.10.0-rc-1", activeRc = true, rcFor = "9.10.0", checksum = ChecksumsTest.DISTRIBUTION_RC, wrapperChecksum = ChecksumsTest.WRAPPER_RC),
        )
        assertEquals(
            Pin("9.9.0", "9.10.0-rc-1", Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_990), Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC)),
            OfferingPolicy.offer(feeds, pin),
        )
        val unchanged = Feeds(FeedEntry("9.8.2", checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990), null)
        assertEquals(pin, OfferingPolicy.offer(unchanged, pin), "an unchanged version keeps the checksums pinned when it was first offered")
    }
}
