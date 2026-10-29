package gradlenew.versions

data class Pin(val stable: String, val rc: String?) {
    init {
        require(GradleVersion.parseOrNull(stable)?.isFinal == true) { "The pinned stable version must be a final Gradle release, got '$stable'" }
        require(rc == null || GradleVersion.parseOrNull(rc)?.isReleaseCandidate == true) { "The pinned RC must be a Gradle release candidate, got '$rc'" }
    }
}
