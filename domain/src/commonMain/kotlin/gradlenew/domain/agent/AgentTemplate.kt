package gradlenew.domain.agent

object AgentTemplate {

    const val ORIGIN = "{{origin}}"

    private val placeholder = Regex("\\{\\{[^}]*\\}\\}")

    fun render(template: String, origin: String): String {
        val rendered = template.replace(ORIGIN, origin)
        val unknown = placeholder.find(rendered)
        require(unknown == null) { "Unknown placeholder '${unknown!!.value}' in an agent template." }
        return rendered
    }
}
