package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChecksumsTest {

    @Test
    fun acceptsLowerCaseSha256Digests() {
        val checksums = Checksums(DISTRIBUTION_982, WRAPPER_982)
        assertEquals(DISTRIBUTION_982, checksums.distribution)
        assertEquals(WRAPPER_982, checksums.wrapper)
    }

    @Test
    fun rejectsAnythingButSha256Digests() {
        assertFailsWith<IllegalArgumentException> { Checksums("abc", WRAPPER_982) }
        assertFailsWith<IllegalArgumentException> { Checksums(DISTRIBUTION_982, WRAPPER_982.uppercase()) }
        assertFailsWith<IllegalArgumentException> { Checksums(DISTRIBUTION_982.replaceFirst('2', 'g'), WRAPPER_982) }
    }

    @Test
    fun aFeedEntryCarriesItsChecksumsWhenItHasBoth() {
        assertEquals(Checksums(DISTRIBUTION_982, WRAPPER_982), FeedEntry("9.8.2", checksum = DISTRIBUTION_982, wrapperChecksum = WRAPPER_982).checksums())
        assertNull(FeedEntry("9.8.2", checksum = DISTRIBUTION_982).checksums())
        assertNull(FeedEntry("9.8.2").checksums())
    }

    companion object {
        const val DISTRIBUTION_982 = "21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"
        const val WRAPPER_982 = "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98"
        const val DISTRIBUTION_990 = "1111111111111111111111111111111111111111111111111111111111111111"
        const val WRAPPER_990 = "2222222222222222222222222222222222222222222222222222222222222222"
        const val DISTRIBUTION_RC = "3333333333333333333333333333333333333333333333333333333333333333"
        const val WRAPPER_RC = "4444444444444444444444444444444444444444444444444444444444444444"
    }
}
