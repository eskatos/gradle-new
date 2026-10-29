package gradlenew.versions

object OfferingPolicy {

    fun offer(feeds: Feeds, pin: Pin): Pin {
        val pinnedStable = GradleVersion.parse(pin.stable)
        val stable = feeds.current?.finalRelease()?.takeIf { it > pinnedStable } ?: pinnedStable
        val rc = feeds.releaseCandidate?.activeReleaseCandidate()?.takeIf { it.base > stable }
        return Pin(stable.toString(), rc?.toString())
    }

    private fun FeedEntry.finalRelease(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isFinal && !broken && !snapshot && !nightly && !releaseNightly && rcFor.isEmpty() && milestoneFor.isEmpty() }

    private fun FeedEntry.activeReleaseCandidate(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isReleaseCandidate && activeRc && !broken && !snapshot && !nightly && !releaseNightly }
            ?.takeIf { candidate -> GradleVersion.parseOrNull(rcFor)?.let { it.compareTo(candidate.base) == 0 } == true }
}
