package gradlenew.adapterjson

import gradlenew.domain.model.OfferedVersions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionsJsonTest {

    @Test
    fun encodesStableAbsentRcAndPreviousMajor() {
        assertEquals(
            "{\n    \"stable\": \"9.8.2\",\n    \"rc\": null,\n    \"previous\": \"8.14.6\"\n}",
            VersionsJson.encode(OfferedVersions("9.8.2", null, "8.14.6")),
        )
    }

    @Test
    fun roundTripsWithAnRc() {
        val versions = OfferedVersions("9.8.2", "9.9.0-rc-1", "8.14.6")
        assertEquals(versions, VersionsJson.decode(VersionsJson.encode(versions)))
    }

    @Test
    fun decodesAnAbsentRcAndRequiresThePreviousMajor() {
        assertEquals(OfferedVersions("9.8.2", null, "8.14.6"), VersionsJson.decode("""{"stable":"9.8.2","rc":null,"previous":"8.14.6"}"""))
        assertEquals(OfferedVersions("9.8.2", null, "8.14.6"), VersionsJson.decode("""{"stable":"9.8.2","previous":"8.14.6"}"""))
        assertFailsWith<IllegalArgumentException> { VersionsJson.decode("""{"stable":"9.8.2","rc":null}""") }
    }
}
