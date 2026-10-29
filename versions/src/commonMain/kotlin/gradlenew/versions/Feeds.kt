package gradlenew.versions

data class FeedEntry(
    val version: String,
    val activeRc: Boolean = false,
    val rcFor: String = "",
    val milestoneFor: String = "",
    val broken: Boolean = false,
    val snapshot: Boolean = false,
    val nightly: Boolean = false,
    val releaseNightly: Boolean = false,
    val checksum: String = "",
    val wrapperChecksum: String = "",
) {
    fun checksums(): Checksums? =
        if (checksum.isBlank() || wrapperChecksum.isBlank()) null else Checksums(checksum.trim().lowercase(), wrapperChecksum.trim().lowercase())
}

data class Feeds(val current: FeedEntry?, val releaseCandidate: FeedEntry?)
