package gradlenew.substitution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FileSetTest {

    @Test
    fun ordersEntriesByPath() {
        val files = FileSet.of(text("b.txt", "b"), text("a/z.txt", "z"), text("a.txt", "a"))

        assertEquals(listOf("a.txt", "a/z.txt", "b.txt"), files.entries.map { it.path })
    }

    @Test
    fun rejectsDuplicatePathsAndInvalidPaths() {
        assertFailsWith<IllegalArgumentException> { FileSet.of(text("a.txt", "1"), text("a.txt", "2")) }
        assertFailsWith<IllegalArgumentException> { FileEntry("/abs.txt", byteArrayOf(), false) }
        assertFailsWith<IllegalArgumentException> { FileEntry("a\\b.txt", byteArrayOf(), false) }
        assertFailsWith<IllegalArgumentException> { FileEntry("a/../b.txt", byteArrayOf(), false) }
        assertFailsWith<IllegalArgumentException> { FileEntry("", byteArrayOf(), false) }
    }

    @Test
    fun comparesByPathContentAndExecutableFlag() {
        val files = FileSet.of(text("gradlew", "#!/bin/sh", executable = true), text("a.txt", "a"))

        assertEquals(files, FileSet.of(text("a.txt", "a"), text("gradlew", "#!/bin/sh", executable = true)))
        assertEquals(files.hashCode(), FileSet.of(text("a.txt", "a"), text("gradlew", "#!/bin/sh", executable = true)).hashCode())
        assertNotEquals(files, FileSet.of(text("a.txt", "a"), text("gradlew", "#!/bin/sh")))
        assertNotEquals(files, FileSet.of(text("a.txt", "b"), text("gradlew", "#!/bin/sh", executable = true)))
    }

    @Test
    fun describesDifferences() {
        val expected = FileSet.of(text("same.txt", "x"), text("changed.txt", "old"), text("missing.txt", "m"), text("gradlew", "s", executable = true))
        val actual = FileSet.of(text("same.txt", "x"), text("changed.txt", "new"), text("extra.txt", "e"), text("gradlew", "s"))

        assertEquals(
            listOf(
                "changed.txt: content differs",
                "extra.txt: unexpected",
                "gradlew: executable false, expected true",
                "missing.txt: missing",
            ),
            expected.differencesFrom(actual),
        )
        assertTrue(expected.differencesFrom(expected).isEmpty())
    }

    @Test
    fun movesEntriesUnderARootDirectory() {
        val files = FileSet.of(text("settings.gradle.kts", "s"), text("gradlew", "w", executable = true))

        val rooted = files.under("demo-app")

        assertEquals(listOf("demo-app/gradlew", "demo-app/settings.gradle.kts"), rooted.entries.map { it.path })
        assertTrue(rooted.entries.first().executable)
    }

    private fun text(path: String, content: String, executable: Boolean = false) =
        FileEntry(path, content.encodeToByteArray(), executable)
}
