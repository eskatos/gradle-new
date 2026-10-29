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

    @Test
    fun carriesTheParameterARejectionConcerns() {
        val text = ErrorJson.encode("Package name is not supported for 'cpp-application' build type.", "package")

        assertEquals("{\n    \"error\": \"Package name is not supported for 'cpp-application' build type.\",\n    \"parameter\": \"package\"\n}", text)
        assertEquals("package", ErrorJson.parameter(text))
        assertEquals("Package name is not supported for 'cpp-application' build type.", ErrorJson.decode(text))
        assertEquals(null, ErrorJson.parameter(ErrorJson.encode("Option 'x' is given more than once.", null)))
    }
}
