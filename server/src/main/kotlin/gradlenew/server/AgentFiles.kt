package gradlenew.server

import gradlenew.adapterjson.DiscoveryIndexJson
import gradlenew.domain.agent.AgentTemplate
import gradlenew.domain.agent.DiscoveryIndex
import java.io.File

class AgentFiles(private val dir: File) {

    private val skillTemplate by lazy { dir.resolve("SKILL.md").readText() }
    private val llmsTemplate by lazy { dir.resolve("llms.txt").readText() }

    fun skill(origin: String): ByteArray = AgentTemplate.render(skillTemplate, origin).encodeToByteArray()

    fun llms(origin: String): ByteArray = AgentTemplate.render(llmsTemplate, origin).encodeToByteArray()

    fun index(origin: String): ByteArray {
        val skill = skill(origin)
        return DiscoveryIndexJson.encode(DiscoveryIndex.of(skill.decodeToString(), SKILL_URL, "sha256:" + sha256(skill))).encodeToByteArray()
    }

    companion object {
        const val INDEX_PATH = "/.well-known/agent-skills/index.json"
        const val SKILL_URL = "gradle-new/SKILL.md"
        const val SKILL_PATH = "/.well-known/agent-skills/$SKILL_URL"
        const val LLMS_PATH = "/llms.txt"
    }
}
