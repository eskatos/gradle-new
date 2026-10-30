package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfferingPolicyTest {

    private val pinned = pin("9.8.2")

    @Test
    fun keepsThePinWhenTheFeedsMatchIt() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.unchanged, pinned, testJava))
    }

    @Test
    fun offersAnActiveReleaseCandidateAboveTheStableVersion() {
        assertEquals(pin("9.8.2", "9.9.0-rc-1"), OfferingPolicy.offer(RecordedFeeds.activeRc, pinned, testJava))
    }

    @Test
    fun retiresTheReleaseCandidateWhenItsFinalShips() {
        assertEquals(pin("9.9.0"), OfferingPolicy.offer(RecordedFeeds.finalRetiresRc, pin("9.8.2", "9.9.0-rc-2"), testJava))
    }

    @Test
    fun offersANewStableAndANewerReleaseCandidateTogether() {
        assertEquals(pin("9.9.0", "9.10.0-rc-1"), OfferingPolicy.offer(RecordedFeeds.newStableAndRc, pinned, testJava))
    }

    @Test
    fun keepsThePinnedStableWhenTheNewOneIsBroken() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.brokenStable, pinned, testJava))
    }

    @Test
    fun clearsAPinnedReleaseCandidateThatIsNoLongerActive() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.inactiveRc, pin("9.8.2", "9.9.0-rc-1"), testJava))
    }

    @Test
    fun ignoresMilestonesAndNightlies() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.milestoneAndNightly, pinned, testJava))
    }

    @Test
    fun neverOffersALowerStableThanThePinnedOne() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.olderStable, pinned, testJava))
    }

    @Test
    fun dropsAReleaseCandidateWhoseBaseIsNotAboveTheOfferedStable() {
        val stale = Feeds(FeedEntry("9.9.0"), FeedEntry("9.9.0-rc-3", activeRc = true, rcFor = "9.9.0"))
        assertEquals(pin("9.9.0"), OfferingPolicy.offer(stale, pinned, testJava))
    }

    @Test
    fun ignoresABrokenReleaseCandidateAndOneThatDisagreesWithItsBase() {
        assertEquals(pinned, OfferingPolicy.offer(Feeds(FeedEntry("9.8.2"), FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0", broken = true)), pinned, testJava))
        assertEquals(pinned, OfferingPolicy.offer(Feeds(FeedEntry("9.8.2"), FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.10.0")), pinned, testJava))
    }

    @Test
    fun keepsThePinnedStableWhenTheCurrentFeedIsEmpty() {
        assertEquals(pinned, OfferingPolicy.offer(Feeds(null, null), pinned, testJava))
    }

    @Test
    fun anOfferedVersionCarriesTheFeedsChecksumsAndAKeptOneKeepsThePinnedChecksums() {
        val pinned = Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982)
        val pin = pin("9.8.2", stableChecksums = pinned)
        val feeds = Feeds(
            FeedEntry("9.9.0", checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990),
            FeedEntry("9.10.0-rc-1", activeRc = true, rcFor = "9.10.0", checksum = ChecksumsTest.DISTRIBUTION_RC, wrapperChecksum = ChecksumsTest.WRAPPER_RC),
        )
        assertEquals(
            pin("9.9.0", "9.10.0-rc-1", stableChecksums = Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_990), rcChecksums = Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC)),
            OfferingPolicy.offer(feeds, pin, testJava),
        )
        val unchanged = Feeds(FeedEntry("9.8.2", checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990), null)
        assertEquals(pin, OfferingPolicy.offer(unchanged, pin, testJava), "an unchanged version keeps the checksums pinned when it was first offered")
    }
}
