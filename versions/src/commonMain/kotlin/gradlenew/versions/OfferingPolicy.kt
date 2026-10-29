package gradlenew.versions

object OfferingPolicy {

    fun offer(feeds: Feeds, pin: Pin): Pin {
        val pinnedStable = GradleVersion.parse(pin.stable)
        val newStable = feeds.current?.takeIf { entry -> entry.finalRelease()?.let { it > pinnedStable } == true }
        val stable = newStable?.finalRelease() ?: pinnedStable
        val rcEntry = feeds.releaseCandidate?.takeIf { entry -> entry.activeReleaseCandidate()?.let { it.base > stable } == true }
        val rc = rcEntry?.activeReleaseCandidate()?.toString()
        return Pin(
            stable.toString(),
            rc,
            if (newStable != null) newStable.checksums() else pin.stableChecksums,
            when {
                rc == null -> null
                rc == pin.rc -> pin.rcChecksums
                else -> rcEntry.checksums()
            },
        )
    }

    private fun FeedEntry.finalRelease(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isFinal && !broken && !snapshot && !nightly && !releaseNightly && rcFor.isEmpty() && milestoneFor.isEmpty() }

    private fun FeedEntry.activeReleaseCandidate(): GradleVersion? =
        GradleVersion.parseOrNull(version)
            ?.takeIf { it.isReleaseCandidate && activeRc && !broken && !snapshot && !nightly && !releaseNightly }
            ?.takeIf { candidate -> GradleVersion.parseOrNull(rcFor)?.let { it.compareTo(candidate.base) == 0 } == true }
}
