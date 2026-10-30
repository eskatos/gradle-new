package gradlenew.domain.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentTemplateTest {

    @Test
    fun replacesEveryOrigin() {
        assertEquals(
            "Read https://gradle.example/api/model, then https://gradle.example/api/generate.",
            AgentTemplate.render("Read {{origin}}/api/model, then {{origin}}/api/generate.", "https://gradle.example"),
        )
        assertEquals("No placeholder.", AgentTemplate.render("No placeholder.", "https://gradle.example"))
    }

    @Test
    fun failsOnAnyOtherPlaceholder() {
        val failure = assertFailsWith<IllegalArgumentException> { AgentTemplate.render("{{origin}}/api/{{endpoint}}", "https://gradle.example") }
        assertEquals("Unknown placeholder '{{endpoint}}' in an agent template.", failure.message)
        assertFailsWith<IllegalArgumentException> { AgentTemplate.render("{{ origin }}", "https://gradle.example") }
        assertFailsWith<IllegalArgumentException> { AgentTemplate.render("{{}}", "https://gradle.example") }
    }
}
