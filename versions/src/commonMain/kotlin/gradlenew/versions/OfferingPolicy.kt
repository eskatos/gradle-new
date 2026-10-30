package gradlenew.versions

object OfferingPolicy {

    fun offer(feeds: Feeds, pin: Pin, javaFor: (String) -> Int): Pin {
        val pinnedStable = GradleVersion.parse(pin.stable.version)
        val newStable = feeds.current?.takeIf { entry -> entry.finalRelease()?.let { it > pinnedStable } == true }
        val stable = newStable?.finalRelease() ?: pinnedStable
        val rcEntry = feeds.releaseCandidate?.takeIf { entry -> entry.activeReleaseCandidate()?.let { it.base > stable } == true }
        val rc = rcEntry?.activeReleaseCandidate()?.toString()
        return Pin(
            if (newStable != null) newStable.pinned(javaFor) else pin.stable,
            pin.previous,
            when {
                rc == null -> null
                rc == pin.rc?.version -> pin.rc
                else -> rcEntry.pinned(javaFor)
            },
        )
    }

    private fun FeedEntry.pinned(javaFor: (String) -> Int): PinnedVersion = PinnedVersion(version, javaFor(version), checksums())

    private fun FeedEntry.finalRelease(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isFinal && !broken && !snapshot && !nightly && !releaseNightly && rcFor.isEmpty() && milestoneFor.isEmpty() }

    private fun FeedEntry.activeReleaseCandidate(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isReleaseCandidate && activeRc && !broken && !snapshot && !nightly && !releaseNightly }
            ?.takeIf { candidate -> GradleVersion.parseOrNull(rcFor)?.let { it.compareTo(candidate.base) == 0 } == true }
}
