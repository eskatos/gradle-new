package gradlenew.domain.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscoveryIndexTest {

    private val digest = "sha256:" + "0123456789abcdef".repeat(4)
    private val skill = "---\nname: gradle-new\ndescription: Create a new Gradle build.\n---\n\n# Body\n\nname: not front matter\n"

    @Test
    fun describesASkillFileByItsFrontMatter() {
        assertEquals(
            DiscoveryIndex(listOf(SkillEntry("gradle-new", "Create a new Gradle build.", SkillEntry.SKILL_MD, "gradle-new/SKILL.md", digest))),
            DiscoveryIndex.of(skill, "gradle-new/SKILL.md", digest),
        )
        assertEquals("https://schemas.agentskills.io/discovery/0.2.0/schema.json", DiscoveryIndex.SCHEMA)
    }

    @Test
    fun rejectsASkillTheSkillsCliWouldRefuse() {
        for (invalid in listOf("No front matter", "---\nname: gradle-new\n", "---\ndescription: d\n---\n", "---\nname: gradle-new\n---\n")) {
            assertFailsWith<IllegalArgumentException>(invalid) { DiscoveryIndex.of(invalid, "gradle-new/SKILL.md", digest) }
        }
        for (name in listOf("Gradle-New", "gradle--new", "-gradle", "gradle-", "gradle_new", "a".repeat(65))) {
            assertFailsWith<IllegalArgumentException>(name) { DiscoveryIndex.of("---\nname: $name\ndescription: d\n---\n", "x/SKILL.md", digest) }
        }
        assertFailsWith<IllegalArgumentException> { DiscoveryIndex.of("---\nname: gradle-new\ndescription: ${"d".repeat(1025)}\n---\n", "x/SKILL.md", digest) }
        assertFailsWith<IllegalArgumentException> { DiscoveryIndex.of(skill, "gradle-new/SKILL.md", "sha256:ABC") }
    }
}
