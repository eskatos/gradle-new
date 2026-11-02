package gradlenew.ui

import gradlenew.domain.agent.AgentTemplate
import gradlenew.domain.agent.DiscoveryIndex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentFilesTest {

    private val dir = File(System.getProperty("ui.agentDir") ?: error("ui.agentDir is not set"))
    private val origin = "https://gradle.example"

    private fun rendered(name: String): String = AgentTemplate.render(dir.resolve(name).readText(), origin)

    @Test
    fun renderingForAnOriginLeavesNoPlaceholder() {
        for (name in listOf("SKILL.md", "llms.txt")) {
            assertTrue("{{" !in rendered(name) && "}}" !in rendered(name), name)
            assertTrue(rendered(name).contains("$origin/api/model"), name)
        }
    }

    @Test
    fun everyLinkInLlmsTxtIsAbsoluteOnTheOrigin() {
        val links = Regex("\\]\\(([^)]*)\\)").findAll(rendered("llms.txt")).map { it.groupValues[1] }.toList()
        assertTrue(links.size >= 5, links.toString())
        for (link in links) assertTrue(link.startsWith("$origin/"), link)
        assertTrue(links.contains("$origin/.well-known/agent-skills/gradle-new/SKILL.md"), links.toString())
    }

    @Test
    fun bothFilesOfferThePreviousMajor() {
        val llms = rendered("llms.txt")
        assertTrue(llms.contains("`version` (`stable` by default, `rc` or `previous`)"), llms)
        assertTrue(llms.contains("($origin/api/generate?type=java-application&project-name=demo-app&version=previous)"), llms)
        val skill = rendered("SKILL.md")
        assertTrue(skill.contains("`?version=previous` only when asked for the previous Gradle major"), skill)
    }

    @Test
    fun bothFilesTellAgentsToAskRatherThanGuess() {
        val skill = rendered("SKILL.md")
        assertTrue(skill.contains("Never guess the build type or the project name: ask, offering the `buildTypes` by component type then language, and the target directory's name."), skill)
        assertTrue(skill.contains("ask once whether Gradle's defaults are fine"), skill)
        val llms = rendered("llms.txt")
        assertTrue(llms.contains("When the request leaves the build type or the project name open, ask the user rather than guessing"), llms)
    }

    @Test
    fun theSkillKeepsOnlyTheInstructionsTheEvaluationShowedToMatter() {
        val skill = rendered("SKILL.md")
        assertTrue(skill.contains("\ndescription: Create a new Gradle build with gradle-new instead of writing build files by hand.\n"), skill)
        assertFalse(skill.contains("Do not write the build files of a new Gradle build from memory."), skill)
        assertFalse(skill.contains("Send only the options whose value differs from the default."), skill)
    }

    @Test
    fun theSkillIsOneTheSkillsCliAccepts() {
        val skill = rendered("SKILL.md")
        assertEquals("gradle-new", DiscoveryIndex.of(skill, "gradle-new/SKILL.md", "sha256:" + "0".repeat(64)).skills.single().name)
        assertTrue(skill.contains("$origin/api/generate?type=<type>&project-name=<name>"), skill)
        assertTrue(skill.contains("$origin/llms.txt"), skill)
    }
}
