package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleVersionTest {

    @Test
    fun parsesEveryFormThatTheVersionsFeedUses() {
        assertEquals(GradleVersion(listOf(9, 8, 2), Stage.Final), GradleVersion.parse("9.8.2"))
        assertEquals(GradleVersion(listOf(8, 14), Stage.Final), GradleVersion.parse("8.14"))
        assertEquals(GradleVersion(listOf(9, 8, 0), Stage.ReleaseCandidate(3)), GradleVersion.parse("9.8.0-rc-3"))
        assertEquals(GradleVersion(listOf(8, 14), Stage.ReleaseCandidate(1)), GradleVersion.parse("8.14-rc-1"))
        assertEquals(GradleVersion(listOf(9, 9, 0), Stage.Milestone(2, "")), GradleVersion.parse("9.9.0-milestone-2"))
        assertEquals(GradleVersion(listOf(9, 0), Stage.Milestone(7, "")), GradleVersion.parse("9.0-milestone-7"))
        assertEquals(GradleVersion(listOf(1, 0), Stage.Milestone(8, "a")), GradleVersion.parse("1.0-milestone-8a"))
        assertEquals(GradleVersion(listOf(9, 10, 0), Stage.Snapshot("20261029002136+0000")), GradleVersion.parse("9.10.0-20261029002136+0000"))
    }

    @Test
    fun rejectsWhatIsNotAGradleVersion() {
        for (text in listOf("", "9", "9.x", "9.8.2-beta-1", "v9.8.2", "9.8.2 ", "9..2", "9.8.0-rc-", "9.8.0-rc-x")) {
            assertNull(GradleVersion.parseOrNull(text), "'$text' parsed")
        }
        assertFailsWith<IllegalArgumentException> { GradleVersion.parse("9.x") }
    }

    @Test
    fun printsTheOriginalText() {
        for (text in listOf("9.8.2", "8.14", "9.8.0-rc-3", "1.0-milestone-8a", "9.9.0-20261029024749+0000")) {
            assertEquals(text, GradleVersion.parse(text).toString())
        }
    }

    @Test
    fun exposesTheBaseVersionAndStage() {
        assertEquals(GradleVersion.parse("9.9.0"), GradleVersion.parse("9.9.0-rc-1").base)
        assertEquals(GradleVersion.parse("8.14"), GradleVersion.parse("8.14-rc-2").base)
        assertTrue(GradleVersion.parse("9.8.2").isFinal)
        assertTrue(GradleVersion.parse("9.9.0-rc-1").isReleaseCandidate)
        assertTrue(!GradleVersion.parse("9.9.0-milestone-1").isFinal && !GradleVersion.parse("9.9.0-milestone-1").isReleaseCandidate)
        assertTrue(!GradleVersion.parse("9.9.0-20261029024749+0000").isFinal)
    }

    @Test
    fun ordersLikeGradleReleases() {
        val ordered = listOf(
            "1.0-milestone-8", "1.0-milestone-8a", "1.0",
            "8.14-rc-1", "8.14-rc-3", "8.14", "8.14.6",
            "9.0-milestone-7", "9.8.0-milestone-1", "9.8.0-rc-1", "9.8.0-rc-3", "9.8.0", "9.8.1", "9.8.2",
            "9.9.0-20261029024749+0000", "9.9.0-milestone-1", "9.9.0-milestone-2", "9.9.0-rc-1", "9.9.0",
            "9.10.0-20261029002136+0000", "9.10.0-rc-1", "9.10.0",
        ).map(GradleVersion::parse)

        assertEquals(ordered, ordered.reversed().sorted())
        assertEquals(ordered, ordered.shuffled(kotlin.random.Random(42)).sorted())
    }

    @Test
    fun treatsMissingPatchNumbersAsZero() {
        assertEquals(0, GradleVersion.parse("9.8").compareTo(GradleVersion.parse("9.8.0")))
        assertTrue(GradleVersion.parse("9.8.0-rc-1") < GradleVersion.parse("9.8"))
    }
}
