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
)

data class Feeds(val current: FeedEntry?, val releaseCandidate: FeedEntry?)
