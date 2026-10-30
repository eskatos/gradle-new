package gradlenew.versions

object RecordedFeeds {

    private val current982 = FeedEntry("9.8.2", current = true)
    private val final990 = FeedEntry("9.9.0", current = true)
    private val previous8146 = FeedEntry("8.14.6")

    val unchanged = Feeds(listOf(current982, previous8146))
    val activeRc = Feeds(listOf(current982, FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0"), previous8146))
    val finalRetiresRc = Feeds(listOf(final990, FeedEntry("9.8.2"), previous8146))
    val newStableAndRc = Feeds(listOf(final990, FeedEntry("9.10.0-rc-1", activeRc = true, rcFor = "9.10.0"), FeedEntry("9.8.2"), previous8146))
    val brokenStable = Feeds(listOf(FeedEntry("9.9.0", current = true, broken = true), FeedEntry("9.8.2"), previous8146))
    val inactiveRc = Feeds(listOf(current982, FeedEntry("9.9.0-rc-1", activeRc = false, rcFor = "9.9.0"), previous8146))
    val milestoneAndNightly = Feeds(
        listOf(
            current982,
            FeedEntry("9.9.0-20261029024749+0000", snapshot = true, releaseNightly = true),
            FeedEntry("9.9.0-milestone-2", activeRc = true, milestoneFor = "9.9.0"),
            previous8146,
        ),
    )
    val olderStable = Feeds(listOf(FeedEntry("8.14.6", current = true)))
    val previousPatch = Feeds(
        listOf(
            current982,
            FeedEntry("8.14.7"),
            previous8146,
            FeedEntry("8.14.8", broken = true),
            FeedEntry("8.14.9-rc-1", rcFor = "8.14.9"),
            FeedEntry("8.15.0-milestone-1", milestoneFor = "8.15.0"),
            FeedEntry("8.15.0-20261029130000+0000", snapshot = true, releaseNightly = true),
        ),
    )
    val majorBump = Feeds(
        listOf(
            FeedEntry("10.0.0", current = true),
            FeedEntry("9.10.0-rc-1", rcFor = "9.10.0"),
            FeedEntry("9.9.0"),
            FeedEntry("9.9.1"),
            FeedEntry("9.8.2"),
            previous8146,
        ),
    )
}
