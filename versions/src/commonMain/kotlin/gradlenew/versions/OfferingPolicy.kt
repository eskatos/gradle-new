package gradlenew.versions

object OfferingPolicy {

    fun offer(feeds: Feeds, pin: Pin, javaFor: (String) -> Int): Pin {
        val current = current(feeds)
        val pinnedStable = GradleVersion.parse(pin.stable.version)
        val newStable = current.takeIf { entry -> entry.finalRelease()?.let { it > pinnedStable } == true }
        val stable = newStable?.finalRelease() ?: pinnedStable
        val rcEntry = feeds.entries
            .mapNotNull { entry -> entry.activeReleaseCandidate()?.takeIf { it.base > stable }?.let { entry to it } }
            .maxByOrNull { (_, version) -> version }?.first
        return Pin(
            if (newStable != null) newStable.pinned(javaFor) else pin.stable,
            previous(feeds, pin, stable, javaFor),
            when {
                rcEntry == null -> null
                rcEntry.version == pin.rc?.version -> pin.rc
                else -> rcEntry.pinned(javaFor)
            },
        )
    }

    private fun current(feeds: Feeds): FeedEntry {
        val current = feeds.entries.filter { it.current }
        require(current.isNotEmpty()) { "The versions feed has no current release" }
        require(current.size == 1) { "The versions feed has several current releases: ${current.map { it.version }}" }
        return current.single()
    }

    private fun previous(feeds: Feeds, pin: Pin, stable: GradleVersion, javaFor: (String) -> Int): PinnedVersion {
        val major = stable.major - 1
        val pinned = GradleVersion.parse(pin.previous.version)
        val latest = feeds.entries
            .mapNotNull { entry -> entry.finalRelease()?.takeIf { it.major == major }?.let { entry to it } }
            .maxByOrNull { (_, version) -> version }
        return when {
            latest != null && (pinned.major != major || latest.second > pinned) -> latest.first.pinned(javaFor)
            pinned.major == major -> pin.previous
            else -> throw IllegalArgumentException("The versions feed has no final release of Gradle $major to offer as the previous major")
        }
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
