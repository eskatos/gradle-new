package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalsTest {

    @Test
    fun proposesNothingWhenTheOfferMatchesThePin() {
        assertEquals(emptyList(), Proposals.of(pin("9.8.2"), pin("9.8.2")))
        assertEquals(emptyList(), Proposals.of(pin("9.8.2", "9.9.0-rc-1"), pin("9.8.2", "9.9.0-rc-1")))
    }

    @Test
    fun proposesAReleaseCandidateAlone() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-1", pin("9.8.2", "9.9.0-rc-1"))),
            Proposals.of(pin("9.8.2"), pin("9.8.2", "9.9.0-rc-1")),
        )
    }

    @Test
    fun replacesAReleaseCandidateWithANewerOne() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-2", pin("9.8.2", "9.9.0-rc-2"))),
            Proposals.of(pin("9.8.2", "9.9.0-rc-1"), pin("9.8.2", "9.9.0-rc-2")),
        )
    }

    @Test
    fun aNewStableThatRetiresThePinnedReleaseCandidateIsOneProposal() {
        assertEquals(
            listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0"))),
            Proposals.of(pin("9.8.2", "9.9.0-rc-2"), pin("9.9.0")),
        )
    }

    @Test
    fun aNewStableKeepsAReleaseCandidateThatIsStillAboveIt() {
        assertEquals(
            listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0", "9.10.0-rc-1"))),
            Proposals.of(pin("9.8.2", "9.10.0-rc-1"), pin("9.9.0", "9.10.0-rc-1")),
        )
    }

    @Test
    fun aNewStableAndANewReleaseCandidateAreSeparateProposals() {
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0")),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", pin("9.8.2", "9.10.0-rc-1")),
            ),
            Proposals.of(pin("9.8.2"), pin("9.9.0", "9.10.0-rc-1")),
        )
    }

    @Test
    fun stopsOfferingAReleaseCandidateThatIsNoLongerActive() {
        assertEquals(
            listOf(Proposal(Slot.RC, "Stop offering Gradle 9.9.0-rc-1", pin("9.8.2"))),
            Proposals.of(pin("9.8.2", "9.9.0-rc-1"), pin("9.8.2")),
        )
    }

    @Test
    fun aPatchReleaseAndAnInactiveReleaseCandidateAreSeparateProposals() {
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.8.3", pin("9.8.3", "9.9.0-rc-1")),
                Proposal(Slot.RC, "Stop offering Gradle 9.9.0-rc-1", pin("9.8.2")),
            ),
            Proposals.of(pin("9.8.2", "9.9.0-rc-1"), pin("9.8.3")),
        )
    }

    @Test
    fun eachSlotHasItsOwnBranch() {
        assertEquals("offer-gradle/stable", Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0")).branch)
        assertEquals("offer-gradle/rc", Proposal(Slot.RC, "Offer Gradle 9.9.0-rc-1", pin("9.8.2", "9.9.0-rc-1")).branch)
    }

    @Test
    fun theRecordedFeedsGiveTheExpectedProposals() {
        val pin = pin("9.8.2")
        assertEquals(1, Proposals.of(pin, OfferingPolicy.offer(RecordedFeeds.activeRc, pin, testJava)).size)
        assertEquals(2, Proposals.of(pin, OfferingPolicy.offer(RecordedFeeds.newStableAndRc, pin, testJava)).size)
        for (feeds in listOf(RecordedFeeds.unchanged, RecordedFeeds.brokenStable, RecordedFeeds.milestoneAndNightly, RecordedFeeds.olderStable, RecordedFeeds.inactiveRc)) {
            assertEquals(emptyList(), Proposals.of(pin, OfferingPolicy.offer(feeds, pin, testJava)), feeds.toString())
        }
    }

    @Test
    fun proposalsCarryTheChecksumsOfWhatTheyChangeAndKeepTheOthers() {
        val stable982 = Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982)
        val stable990 = Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_990)
        val rc = Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC)
        val pin = pin("9.8.2", stableChecksums = stable982)
        val offered = pin("9.9.0", "9.10.0-rc-1", stableChecksums = stable990, rcChecksums = rc)
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0", stableChecksums = stable990)),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", pin("9.8.2", "9.10.0-rc-1", stableChecksums = stable982, rcChecksums = rc)),
            ),
            Proposals.of(pin, offered),
        )
    }
}
