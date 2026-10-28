package gradlenew.adapterjson

import gradlenew.domain.model.OfferedVersions
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionsJsonTest {

    @Test
    fun encodesStableAndAbsentRc() {
        assertEquals("{\n    \"stable\": \"9.8.2\",\n    \"rc\": null\n}", VersionsJson.encode(OfferedVersions("9.8.2", null)))
    }

    @Test
    fun roundTripsWithAnRc() {
        val versions = OfferedVersions("9.8.2", "9.9.0-rc-1")
        assertEquals(versions, VersionsJson.decode(VersionsJson.encode(versions)))
    }

    @Test
    fun decodesAnAbsentRc() {
        assertEquals(OfferedVersions("9.8.2", null), VersionsJson.decode("""{"stable":"9.8.2","rc":null}"""))
        assertEquals(OfferedVersions("9.8.2", null), VersionsJson.decode("""{"stable":"9.8.2"}"""))
    }
}
