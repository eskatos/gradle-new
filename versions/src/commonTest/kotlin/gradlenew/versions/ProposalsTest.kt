package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalsTest {

    @Test
    fun proposesNothingWhenTheOfferMatchesThePin() {
        assertEquals(emptyList(), Proposals.of(Pin("9.8.2", null), Pin("9.8.2", null)))
        assertEquals(emptyList(), Proposals.of(Pin("9.8.2", "9.9.0-rc-1"), Pin("9.8.2", "9.9.0-rc-1")))
    }

    @Test
    fun proposesAReleaseCandidateAlone() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-1", Pin("9.8.2", "9.9.0-rc-1"))),
            Proposals.of(Pin("9.8.2", null), Pin("9.8.2", "9.9.0-rc-1")),
        )
    }

    @Test
    fun replacesAReleaseCandidateWithANewerOne() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-2", Pin("9.8.2", "9.9.0-rc-2"))),
            Proposals.of(Pin("9.8.2", "9.9.0-rc-1"), Pin("9.8.2", "9.9.0-rc-2")),
        )
    }

    @Test
    fun aNewStableThatRetiresThePinnedReleaseCandidateIsOneProposal() {
        assertEquals(
            listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null))),
            Proposals.of(Pin("9.8.2", "9.9.0-rc-2"), Pin("9.9.0", null)),
        )
    }

    @Test
    fun aNewStableKeepsAReleaseCandidateThatIsStillAboveIt() {
        assertEquals(
            listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", "9.10.0-rc-1"))),
            Proposals.of(Pin("9.8.2", "9.10.0-rc-1"), Pin("9.9.0", "9.10.0-rc-1")),
        )
    }

    @Test
    fun aNewStableAndANewReleaseCandidateAreSeparateProposals() {
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null)),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin("9.8.2", "9.10.0-rc-1")),
            ),
            Proposals.of(Pin("9.8.2", null), Pin("9.9.0", "9.10.0-rc-1")),
        )
    }

    @Test
    fun stopsOfferingAReleaseCandidateThatIsNoLongerActive() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Stop offering Gradle 9.9.0-rc-1", Pin("9.8.2", null))),
            Proposals.of(Pin("9.8.2", "9.9.0-rc-1"), Pin("9.8.2", null)),
        )
    }

    @Test
    fun aPatchReleaseAndAnInactiveReleaseCandidateAreSeparateProposals() {
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.8.3", Pin("9.8.3", "9.9.0-rc-1")),
                Proposal(Slot.RC, "Stop offering Gradle 9.9.0-rc-1", Pin("9.8.2", null)),
            ),
            Proposals.of(Pin("9.8.2", "9.9.0-rc-1"), Pin("9.8.3", null)),
        )
    }

    @Test
    fun eachSlotHasItsOwnBranch() {
        assertEquals("offer-gradle/stable", Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null)).branch)
        assertEquals("offer-gradle/rc", Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-1", Pin("9.8.2", "9.9.0-rc-1")).branch)
    }

    @Test
    fun theRecordedFeedsGiveTheExpectedProposals() {
        val pin = Pin("9.8.2", null)
        assertEquals(1, Proposals.of(pin, OfferingPolicy.offer(RecordedFeeds.activeRc, pin)).size)
        assertEquals(2, Proposals.of(pin, OfferingPolicy.offer(RecordedFeeds.newStableAndRc, pin)).size)
        for (feeds in listOf(RecordedFeeds.unchanged, RecordedFeeds.brokenStable, RecordedFeeds.milestoneAndNightly, RecordedFeeds.olderStable, RecordedFeeds.inactiveRc)) {
            assertEquals(emptyList(), Proposals.of(pin, OfferingPolicy.offer(feeds, pin)), feeds.toString())
        }
    }
}
