package gradlenew.adapterjson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorJsonTest {

    @Test
    fun roundTripsAMultiLineMessage() {
        val message = "The requested build script DSL 'yaml' is not supported. Supported DSLs:\n    - 'kotlin'\n    - 'groovy'"

        val json = ErrorJson.encode(message)

        assertTrue(json.contains("\"error\""), json)
        assertEquals(message, ErrorJson.decode(json))
    }
}
