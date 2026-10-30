package gradlenew.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaCompatibilityTest {

    private val gradle8Page = page((8..24).map { java -> java.toString() to running8(java) } + ("25" to "N/A"))

    private val gradle9Page = page(
        (8..16).map { java -> java.toString() to "${running8(java)} to 8.14.x" } +
            listOf("17" to "7.3 and after", "18" to "7.5 and after", "19" to "7.6 and after", "20" to "8.3 and after", "21" to "8.5 and after",
                "22" to "8.8 and after", "23" to "8.10 and after", "24" to "8.14 and after", "25" to "9.1.0 and after", "26" to "9.4.0 and after",
                "27" to "9.8.0 and after", "28" to "N/A"),
    )

    @Test
    fun readsTheJavaVersionsAGradleVersionRunsOnFromItsOwnPage() {
        assertEquals((8..24).toList(), JavaCompatibility.runsOn(gradle8Page, "8.14.6"))
        assertEquals((17..27).toList(), JavaCompatibility.runsOn(gradle9Page, "9.8.2"))
        assertEquals((17..24).toList(), JavaCompatibility.runsOn(gradle9Page, "9.0.0"))
    }

    @Test
    fun takesTheHighestLongTermSupportVersion() {
        assertEquals(21, JavaCompatibility.highestLts(gradle8Page, "8.14.6"))
        assertEquals(25, JavaCompatibility.highestLts(gradle9Page, "9.8.2"))
        assertEquals(21, JavaCompatibility.highestLts(gradle9Page, "9.0.0"))
        assertEquals(25, JavaCompatibility.highestLts(gradle9Page, "10.0.0"))
    }

    @Test
    fun aReleaseCandidateReadsAsTheReleaseItPrepares() {
        assertEquals(25, JavaCompatibility.highestLts(gradle9Page, "9.8.0-rc-3"))
        assertEquals(25, JavaCompatibility.highestLts(gradle9Page, "9.1.0-rc-1"))
    }

    @Test
    fun longTermSupportVersionsAre8And11ThenEveryFourthFrom17() {
        assertEquals(listOf(8, 11, 17, 21, 25, 29, 33), (1..35).filter(JavaCompatibility::isLts))
    }

    @Test
    fun picksTheJavaTableAmongTheOthers() {
        val other = "<table class=\"tableblock\"><thead><tr><th>Kotlin version</th><th>Support</th></tr></thead><tbody><tr><td><p>2.1</p></td><td><p>9.0</p></td></tr></tbody></table>"
        assertEquals(21, JavaCompatibility.highestLts(other + gradle8Page + other, "8.14.6"))
    }

    @Test
    fun failsWhenThePageCannotBeRead() {
        assertFailsWith<IllegalArgumentException> { JavaCompatibility.highestLts("<html><body><p>This page has moved.</p></body></html>", "8.14.6") }
        assertFailsWith<IllegalArgumentException> { JavaCompatibility.highestLts(page(listOf("21" to "soon")), "8.14.6") }
        assertFailsWith<IllegalArgumentException> { JavaCompatibility.highestLts(page(listOf("twenty-one" to "8.5")), "8.14.6") }
        assertFailsWith<IllegalArgumentException> { JavaCompatibility.highestLts(page(listOf("24" to "8.14")), "8.14.6") }
    }

    private fun running8(java: Int): String = when (java) {
        8 -> "2.0"
        9 -> "4.3"
        10 -> "4.7"
        11 -> "5.0"
        12 -> "5.4"
        13 -> "6.0"
        14 -> "6.3"
        15 -> "6.7"
        16 -> "7.0"
        17 -> "7.3"
        18 -> "7.5"
        19 -> "7.6"
        20 -> "8.3"
        21 -> "8.5"
        22 -> "8.8"
        23 -> "8.10"
        else -> "8.14"
    }

    private fun page(rows: List<Pair<String, String>>): String =
        """
        <div class="paragraph"><p>See the table below for the Java version supported by a specific Gradle release:</p></div>
        <table class="tableblock frame-all grid-all stretch">
        <caption class="title">Table 1. Java Compatibility</caption>
        <thead>
        <tr>
        <th class="tableblock halign-left valign-top">Java version</th>
        <th class="tableblock halign-left valign-top">Support for toolchains</th>
        <th class="tableblock halign-left valign-top">Support for running Gradle</th>
        </tr>
        </thead>
        <tbody>
        """.trimIndent() + rows.joinToString("") { (java, running) ->
            "\n<tr>\n<td class=\"tableblock halign-left valign-top\"><p class=\"tableblock\">$java</p></td>\n" +
                "<td class=\"tableblock halign-left valign-top\"><p class=\"tableblock\">N/A</p></td>\n" +
                "<td class=\"tableblock halign-left valign-top\"><p class=\"tableblock\">$running</p></td>\n</tr>"
        } + "\n</tbody>\n</table>\n"
}
