package gradlenew.adapterjson

import gradlenew.domain.agent.DiscoveryIndex
import gradlenew.domain.agent.SkillEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoveryIndexJsonTest {

    @Test
    fun encodesTheSchemaAndEachSkillAsTheSkillsCliReadsThem() {
        val digest = "sha256:" + "0123456789abcdef".repeat(4)
        val index = DiscoveryIndex(listOf(SkillEntry("gradle-new", "Create a new \"Gradle\" build.", SkillEntry.SKILL_MD, "gradle-new/SKILL.md", digest)))

        assertEquals(
            """
            {
                "${'$'}schema": "https://schemas.agentskills.io/discovery/0.2.0/schema.json",
                "skills": [
                    {
                        "name": "gradle-new",
                        "type": "skill-md",
                        "description": "Create a new \"Gradle\" build.",
                        "url": "gradle-new/SKILL.md",
                        "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    }
                ]
            }
            """.trimIndent(),
            DiscoveryIndexJson.encode(index),
        )
    }
}
