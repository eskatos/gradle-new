package gradlenew.domain.model

data class OfferedVersions(val stable: String, val rc: String?) {
    init {
        require(stable.isNotBlank()) { "Stable version must not be blank" }
        require(rc == null || rc.isNotBlank()) { "RC version must be absent or non-blank" }
    }
}
