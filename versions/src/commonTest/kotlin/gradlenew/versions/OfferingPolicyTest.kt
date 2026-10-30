package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfferingPolicyTest {

    private val pinned = pin("9.8.2")
    private val keptVersionsAreNotLookedUp: (String) -> Int = { version -> if (version in setOf("9.8.2", "8.14.6")) error("Java looked up again for $version") else testJava(version) }

    @Test
    fun keepsThePinWhenTheFeedMatchesIt() {
        assertEquals(pinned, OfferingPolicy.offer(RecordedFeeds.unchanged, pinned, keptVersionsAreNotLookedUp))
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
        val stale = Feeds(listOf(FeedEntry("9.9.0", current = true), FeedEntry("9.9.0-rc-3", activeRc = true, rcFor = "9.9.0"), FeedEntry("8.14.6")))
        assertEquals(pin("9.9.0"), OfferingPolicy.offer(stale, pinned, testJava))
    }

    @Test
    fun ignoresABrokenReleaseCandidateAndOneThatDisagreesWithItsBase() {
        val current = FeedEntry("9.8.2", current = true)
        assertEquals(pinned, OfferingPolicy.offer(Feeds(listOf(current, FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0", broken = true))), pinned, testJava))
        assertEquals(pinned, OfferingPolicy.offer(Feeds(listOf(current, FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.10.0"))), pinned, testJava))
    }

    @Test
    fun offersTheHighestOfSeveralActiveReleaseCandidates() {
        val feeds = Feeds(listOf(FeedEntry("9.8.2", current = true), FeedEntry("9.9.0-rc-2", activeRc = true, rcFor = "9.9.0"), FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0")))
        assertEquals(pin("9.8.2", "9.9.0-rc-2"), OfferingPolicy.offer(feeds, pinned, testJava))
    }

    @Test
    fun needsExactlyOneCurrentRelease() {
        assertFailsWith<IllegalArgumentException> { OfferingPolicy.offer(Feeds(listOf(FeedEntry("9.8.2"), FeedEntry("8.14.6"))), pinned, testJava) }
        assertFailsWith<IllegalArgumentException> { OfferingPolicy.offer(Feeds(listOf(FeedEntry("9.8.2", current = true), FeedEntry("9.9.0", current = true))), pinned, testJava) }
        assertFailsWith<IllegalArgumentException> { OfferingPolicy.offer(Feeds(emptyList()), pinned, testJava) }
    }

    @Test
    fun offersTheLatestFinalReleaseOfThePreviousMajorByVersionNotFeedOrder() {
        assertEquals(pin("9.8.2", previous = "8.14.7"), OfferingPolicy.offer(RecordedFeeds.previousPatch, pinned, testJava))
    }

    @Test
    fun keepsThePinnedPreviousMajorWhenTheFeedHasNothingNewerOrNothingAtAll() {
        assertEquals(pin("9.8.2", previous = "8.14.7"), OfferingPolicy.offer(RecordedFeeds.unchanged, pin("9.8.2", previous = "8.14.7"), testJava))
        assertEquals(pinned, OfferingPolicy.offer(Feeds(listOf(FeedEntry("9.8.2", current = true))), pinned, testJava))
    }

    @Test
    fun aNewMajorTakesTheLatestReleaseOfTheMajorItReplaces() {
        assertEquals(pin("10.0.0", previous = "9.9.1"), OfferingPolicy.offer(RecordedFeeds.majorBump, pinned, testJava))
    }

    @Test
    fun aNewMajorWithoutAnyReleaseOfThePreviousMajorFails() {
        assertFailsWith<IllegalArgumentException> { OfferingPolicy.offer(Feeds(listOf(FeedEntry("10.0.0", current = true), FeedEntry("8.14.6"))), pinned, testJava) }
    }

    @Test
    fun eachNewlyOfferedVersionGetsItsJavaAndKeptVersionsKeepTheirs() {
        val javaOf = mapOf("10.0.0" to 29, "9.9.1" to 25)
        val pin = Pin(PinnedVersion("9.8.2", 24), PinnedVersion("8.14.6", 17))
        assertEquals(Pin(PinnedVersion("10.0.0", 29), PinnedVersion("9.9.1", 25)), OfferingPolicy.offer(RecordedFeeds.majorBump, pin) { javaOf.getValue(it) })
        assertEquals(pin, OfferingPolicy.offer(RecordedFeeds.unchanged, pin, keptVersionsAreNotLookedUp))
    }

    @Test
    fun anOfferedVersionCarriesTheFeedsChecksumsAndAKeptOneKeepsThePinnedChecksums() {
        val pinnedChecksums = Checksums(ChecksumsTest.DISTRIBUTION_982, ChecksumsTest.WRAPPER_982)
        val pin = pin("9.8.2", stableChecksums = pinnedChecksums)
        val feeds = Feeds(
            listOf(
                FeedEntry("9.9.0", current = true, checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990),
                FeedEntry("9.10.0-rc-1", activeRc = true, rcFor = "9.10.0", checksum = ChecksumsTest.DISTRIBUTION_RC, wrapperChecksum = ChecksumsTest.WRAPPER_RC),
                FeedEntry("8.14.7", checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_RC),
            ),
        )
        assertEquals(
            pin(
                "9.9.0", "9.10.0-rc-1", previous = "8.14.7",
                stableChecksums = Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_990),
                rcChecksums = Checksums(ChecksumsTest.DISTRIBUTION_RC, ChecksumsTest.WRAPPER_RC),
                previousChecksums = Checksums(ChecksumsTest.DISTRIBUTION_990, ChecksumsTest.WRAPPER_RC),
            ),
            OfferingPolicy.offer(feeds, pin, testJava),
        )
        val unchanged = Feeds(listOf(FeedEntry("9.8.2", current = true, checksum = ChecksumsTest.DISTRIBUTION_990, wrapperChecksum = ChecksumsTest.WRAPPER_990)))
        assertEquals(pin, OfferingPolicy.offer(unchanged, pin, testJava), "an unchanged version keeps the checksums pinned when it was first offered")
    }
}
