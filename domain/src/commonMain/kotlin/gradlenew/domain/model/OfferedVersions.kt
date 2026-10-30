package gradlenew.domain.model

data class OfferedVersions(val stable: String, val rc: String?, val previous: String) {
    init {
        require(stable.isNotBlank()) { "Stable version must not be blank" }
        require(rc == null || rc.isNotBlank()) { "RC version must be absent or non-blank" }
        require(previous.isNotBlank()) { "Previous major version must not be blank" }
    }

    val versions: List<String> get() = listOfNotNull(stable, rc, previous)
}
