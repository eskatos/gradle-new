package gradlenew.versions

object RecordedFeeds {

    private val current982 = FeedEntry("9.8.2")
    private val final990 = FeedEntry("9.9.0")

    val unchanged = Feeds(current982, null)
    val activeRc = Feeds(current982, FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0"))
    val finalRetiresRc = Feeds(final990, null)
    val newStableAndRc = Feeds(final990, FeedEntry("9.10.0-rc-1", activeRc = true, rcFor = "9.10.0"))
    val brokenStable = Feeds(FeedEntry("9.9.0", broken = true), null)
    val inactiveRc = Feeds(current982, FeedEntry("9.9.0-rc-1", activeRc = false, rcFor = "9.9.0"))
    val milestoneAndNightly = Feeds(
        FeedEntry("9.9.0-20261029024749+0000", snapshot = true, releaseNightly = true),
        FeedEntry("9.9.0-milestone-2", activeRc = true, milestoneFor = "9.9.0"),
    )
    val olderStable = Feeds(FeedEntry("8.14.6"), null)
}
