package gradlenew.versions

data class Pin(
    val stable: String,
    val rc: String?,
    val stableChecksums: Checksums? = null,
    val rcChecksums: Checksums? = null,
) {
    init {
        require(GradleVersion.parseOrNull(stable)?.isFinal == true) { "The pinned stable version must be a final Gradle release, got '$stable'" }
        require(rc == null || GradleVersion.parseOrNull(rc)?.isReleaseCandidate == true) { "The pinned RC must be a Gradle release candidate, got '$rc'" }
        require(rc != null || rcChecksums == null) { "Checksums are pinned for an empty RC slot" }
    }

    fun checksumsOf(version: String): Checksums? = when (version) {
        stable -> stableChecksums
        rc -> rcChecksums
        else -> null
    }
}
