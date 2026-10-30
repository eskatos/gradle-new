package gradlenew.versions

data class PinnedVersion(val version: String, val java: Int, val checksums: Checksums? = null) {
    init {
        require(java > 0) { "The Java that runs Gradle $version must be a Java version, got $java" }
    }
}

data class Pin(val stable: PinnedVersion, val previous: PinnedVersion, val rc: PinnedVersion? = null) {
    init {
        val stableVersion = requireNotNull(GradleVersion.parseOrNull(stable.version)?.takeIf { it.isFinal }) {
            "The pinned stable version must be a final Gradle release, got '${stable.version}'"
        }
        require(rc == null || GradleVersion.parseOrNull(rc.version)?.isReleaseCandidate == true) { "The pinned RC must be a Gradle release candidate, got '${rc?.version}'" }
        val previousVersion = GradleVersion.parseOrNull(previous.version)
        require(previousVersion != null && previousVersion.isFinal && previousVersion.major == stableVersion.major - 1) {
            "The pinned previous major must be a final release of Gradle ${stableVersion.major - 1}, got '${previous.version}'"
        }
    }

    val versions: List<PinnedVersion> get() = listOfNotNull(stable, rc, previous)

    operator fun get(slot: Slot): PinnedVersion? = when (slot) {
        Slot.STABLE -> stable
        Slot.RC -> rc
        Slot.PREVIOUS -> previous
    }

    fun with(slot: Slot, pinned: PinnedVersion?): Pin = when (slot) {
        Slot.STABLE -> copy(stable = requireNotNull(pinned) { "The stable slot cannot be empty" })
        Slot.RC -> copy(rc = pinned)
        Slot.PREVIOUS -> copy(previous = requireNotNull(pinned) { "The previous major slot cannot be empty" })
    }
}
