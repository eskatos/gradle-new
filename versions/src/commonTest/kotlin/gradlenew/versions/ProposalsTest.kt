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
        assertEquals("offer-gradle/previous", Proposal(Slot.PREVIOUS, "Offer Gradle 8.14.7 as previous major", pin("9.8.2", previous = "8.14.7")).branch)
    }

    @Test
    fun proposesANewReleaseOfThePreviousMajorAlone() {
        assertEquals(
            listOf(Proposal(Slot.PREVIOUS, "Offer Gradle 8.14.7 as previous major", pin("9.8.2", "9.9.0-rc-1", previous = "8.14.7"))),
            Proposals.of(pin("9.8.2", "9.9.0-rc-1"), pin("9.8.2", "9.9.0-rc-1", previous = "8.14.7")),
        )
    }

    @Test
    fun aNewMajorMovesThePreviousMajorInTheSameProposal() {
        assertEquals(
            listOf(Proposal(Slot.STABLE, "Offer Gradle 10.0.0 with 9.9.1 as previous major", pin("10.0.0", previous = "9.9.1"))),
            Proposals.of(pin("9.8.2"), pin("10.0.0", previous = "9.9.1")),
        )
    }

    @Test
    fun aNewStableOfTheSameMajorAndANewPreviousMajorReleaseAreSeparateProposals() {
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0")),
                Proposal(Slot.PREVIOUS, "Offer Gradle 8.14.7 as previous major", pin("9.8.2", previous = "8.14.7")),
            ),
            Proposals.of(pin("9.8.2"), pin("9.9.0", previous = "8.14.7")),
        )
    }

    @Test
    fun proposalsCarryTheJavaOfWhatTheyChange() {
        val offered = Pin(PinnedVersion("10.0.0", 29), PinnedVersion("9.9.1", 25))
        val proposal = Proposals.of(pin("9.8.2"), offered).single()
        assertEquals(offered, proposal.pin)
    }

    @Test
    fun theRecordedFeedsGiveTheExpectedProposals() {
        val pinned = pin("9.8.2")
        assertEquals(listOf(Slot.RC), Proposals.of(pinned, OfferingPolicy.offer(RecordedFeeds.activeRc, pinned, testJava)).map { it.slot })
        assertEquals(listOf(Slot.STABLE, Slot.RC), Proposals.of(pinned, OfferingPolicy.offer(RecordedFeeds.newStableAndRc, pinned, testJava)).map { it.slot })
        assertEquals(listOf(Slot.PREVIOUS), Proposals.of(pinned, OfferingPolicy.offer(RecordedFeeds.previousPatch, pinned, testJava)).map { it.slot })
        assertEquals(listOf(Slot.STABLE), Proposals.of(pinned, OfferingPolicy.offer(RecordedFeeds.majorBump, pinned, testJava)).map { it.slot })
        for (feeds in listOf(RecordedFeeds.unchanged, RecordedFeeds.brokenStable, RecordedFeeds.milestoneAndNightly, RecordedFeeds.olderStable, RecordedFeeds.inactiveRc)) {
            assertEquals(emptyList(), Proposals.of(pinned, OfferingPolicy.offer(feeds, pinned, testJava)), feeds.toString())
        }
    }

    @Test
    fun proposalsCarryTheChecksumsOfWhatTheyChangeAndKeepTheOthers() {
        val stable982 = Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982)
        val stable990 = Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_990)
        val rc = Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC)
        val pinned = pin("9.8.2", stableChecksums = stable982)
        val offered = pin("9.9.0", "9.10.0-rc-1", stableChecksums = stable990, rcChecksums = rc)
        assertEquals(
            listOf(
                Proposal(Slot.STABLE, "Offer Gradle 9.9.0", pin("9.9.0", stableChecksums = stable990)),
                Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", pin("9.8.2", "9.10.0-rc-1", stableChecksums = stable982, rcChecksums = rc)),
            ),
            Proposals.of(pinned, offered),
        )
    }
}
