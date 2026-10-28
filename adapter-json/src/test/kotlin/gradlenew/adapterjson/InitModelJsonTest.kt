package gradlenew.adapterjson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InitModelJsonTest {

    private val golden = checkNotNull(javaClass.getResource("/model.json")).readText()

    @Test
    fun roundTripsTheModel() {
        assertEquals(sampleModel, InitModelJson.decode(InitModelJson.encode(sampleModel)))
    }

    @Test
    fun encodesExactlyTheGoldenDocument() {
        assertEquals(golden, InitModelJson.encode(sampleModel))
    }

    @Test
    fun decodesTheGoldenDocument() {
        assertEquals(sampleModel, InitModelJson.decode(golden))
    }

    @Test
    fun rejectsADocumentThatViolatesTheModelInvariants() {
        val broken = golden.replace("\"default\": \"kotlin\"", "\"default\": \"scala\"")
        assertFailsWith<IllegalArgumentException> { InitModelJson.decode(broken) }
    }

    @Test
    fun rejectsAMalformedDocument() {
        assertFailsWith<IllegalArgumentException> { InitModelJson.decode("""{"gradleVersion": "9.8.2"}""") }
    }
}
