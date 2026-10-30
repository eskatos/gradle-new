package gradlenew.domain.agent

data class SkillEntry(val name: String, val description: String, val type: String, val url: String, val digest: String) {
    init {
        require(name.length <= 64 && namePattern.matches(name)) { "Skill name '$name' is not lowercase letters and digits separated by single hyphens, at most 64 characters." }
        require(description.isNotBlank() && description.length <= 1024) { "Skill description must be 1 to 1024 characters." }
        require(url.isNotBlank()) { "Skill URL must not be blank." }
        require(digestPattern.matches(digest)) { "Skill digest '$digest' is not sha256:<64 lowercase hex digits>." }
    }

    companion object {
        const val SKILL_MD = "skill-md"
        private val namePattern = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        private val digestPattern = Regex("sha256:[0-9a-f]{64}")
    }
}

data class DiscoveryIndex(val skills: List<SkillEntry>) {

    companion object {
        const val SCHEMA = "https://schemas.agentskills.io/discovery/0.2.0/schema.json"

        fun of(skill: String, url: String, digest: String): DiscoveryIndex {
            val frontMatter = frontMatter(skill)
            val name = requireNotNull(frontMatter["name"]) { "The skill's front matter has no name." }
            val description = requireNotNull(frontMatter["description"]) { "The skill's front matter has no description." }
            return DiscoveryIndex(listOf(SkillEntry(name, description, SkillEntry.SKILL_MD, url, digest)))
        }

        private fun frontMatter(skill: String): Map<String, String> {
            val lines = skill.lines()
            require(lines.firstOrNull() == "---") { "The skill does not start with front matter." }
            val end = lines.drop(1).indexOf("---")
            require(end >= 0) { "The skill's front matter is not closed." }
            return lines.subList(1, end + 1).filter { ": " in it }.associate { it.substringBefore(": ") to it.substringAfter(": ") }
        }
    }
}
