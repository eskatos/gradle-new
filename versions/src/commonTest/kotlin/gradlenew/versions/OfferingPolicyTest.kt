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
}
