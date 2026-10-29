package gradlenew.publisher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArgumentsTest {

    private val required = listOf("--stable", "9.8.2", "--builder-dir", "b", "--catalog", "c.toml", "--output", "out", "--templates-output", "tpl", "--work-dir", "work")

    @Test
    fun parsesAllOptions() {
        val arguments = Arguments.parse(required + listOf("--rc", "9.9.0-rc-1"))
        assertEquals(Arguments("9.8.2", "9.9.0-rc-1", File("b"), File("c.toml"), File("out"), File("tpl"), File("work")), arguments)
    }

    @Test
    fun rcIsOptionalAndBlankMeansAbsent() {
        assertNull(Arguments.parse(required).rc)
        assertNull(Arguments.parse(required + listOf("--rc", "")).rc)
    }

    @Test
    fun rejectsMissingOrMalformedOptions() {
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required.drop(2)) }
        assertFailsWith<IllegalArgumentException> { Arguments.parse(required + "--rc") }
    }
}
