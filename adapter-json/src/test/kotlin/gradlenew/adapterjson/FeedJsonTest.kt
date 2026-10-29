package gradlenew.adapterjson

import gradlenew.versions.FeedEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FeedJsonTest {

    private fun resource(name: String) = requireNotNull(javaClass.getResource("/feeds/$name")) { "missing $name" }.readText()

    @Test
    fun decodesTheCurrentFeedIgnoringFieldsItDoesNotNeed() {
        assertEquals(FeedEntry("9.8.2"), FeedJson.decode(resource("current.json")))
    }

    @Test
    fun decodesAnActiveReleaseCandidate() {
        assertEquals(FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0"), FeedJson.decode(resource("release-candidate-active.json")))
    }

    @Test
    fun decodesANightly() {
        assertEquals(
            FeedEntry("9.9.0-20261029024749+0000", snapshot = true, releaseNightly = true),
            FeedJson.decode(resource("current-nightly.json")),
        )
    }

    @Test
    fun decodesTheEmptyObjectAsNoEntry() {
        assertNull(FeedJson.decode(resource("release-candidate-none.json")))
        assertNull(FeedJson.decode("{}"))
    }

    @Test
    fun defaultsMissingFlagsAndRejectsAnEntryWithoutAVersion() {
        assertEquals(FeedEntry("9.8.2"), FeedJson.decode("""{"version":"9.8.2","somethingNew":[1,2]}"""))
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("""{"activeRc":true}""") }
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("not json") }
    }
}
