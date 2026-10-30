package gradlenew.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfferedVersionsTest {

    @Test
    fun offersAStableAPreviousMajorAndAnOptionalReleaseCandidate() {
        val offered = OfferedVersions("9.8.2", "9.9.0-rc-1", "8.14.6")
        assertEquals(listOf("9.8.2", "9.9.0-rc-1", "8.14.6"), offered.versions)
        assertEquals(listOf("9.8.2", "8.14.6"), OfferedVersions("9.8.2", null, "8.14.6").versions)
    }

    @Test
    fun rejectsBlankVersions() {
        assertFailsWith<IllegalArgumentException> { OfferedVersions(" ", null, "8.14.6") }
        assertFailsWith<IllegalArgumentException> { OfferedVersions("9.8.2", "", "8.14.6") }
        assertFailsWith<IllegalArgumentException> { OfferedVersions("9.8.2", null, "") }
    }
}
