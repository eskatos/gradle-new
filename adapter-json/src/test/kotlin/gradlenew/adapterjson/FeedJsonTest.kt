package gradlenew.adapterjson

import gradlenew.versions.FeedEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedJsonTest {

    private fun resource(name: String) = requireNotNull(javaClass.getResource("/feeds/$name")) { "missing $name" }.readText()

    @Test
    fun decodesEveryEntryOfTheVersionsFeedIgnoringFieldsItDoesNotNeed() {
        assertEquals(
            listOf(
                FeedEntry("9.9.0-20261029024749+0000", snapshot = true, releaseNightly = true, checksum = "98c00712507402df5f78fe92f9db44f31eca3a86165ebe9a08159235381c2de1", wrapperChecksum = "621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8"),
                FeedEntry("9.8.2", current = true, checksum = "21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607", wrapperChecksum = "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98"),
                FeedEntry("9.9.0-rc-1", activeRc = true, rcFor = "9.9.0", checksum = "a068e9c506ea61501c689900aec9010eda810b112e682146b1ae4291df3dab6a", wrapperChecksum = "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                FeedEntry("9.8.0-rc-3", rcFor = "9.8.0", checksum = "a068e9c506ea61501c689900aec9010eda810b112e682146b1ae4291df3dab6a", wrapperChecksum = "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                FeedEntry("8.14.6", checksum = "7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24", wrapperChecksum = "ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0"),
                FeedEntry("1.0-milestone-4", broken = true, checksum = "882c8115a214495f5b6b9df3ba70dec9345698651e8c320036eed272be55ae6a"),
            ),
            FeedJson.decode(resource("all.json")),
        )
    }

    @Test
    fun defaultsMissingFlagsAndRejectsAnEntryWithoutAVersion() {
        assertEquals(listOf(FeedEntry("9.8.2")), FeedJson.decode("""[{"version":"9.8.2","somethingNew":[1,2]}]"""))
        assertEquals(emptyList(), FeedJson.decode("[]"))
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("""[{"activeRc":true}]""") }
    }

    @Test
    fun rejectsAnythingButAListOfEntries() {
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("""{"version":"9.8.2"}""") }
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("""["9.8.2"]""") }
        assertFailsWith<IllegalArgumentException> { FeedJson.decode("not json") }
    }
}
