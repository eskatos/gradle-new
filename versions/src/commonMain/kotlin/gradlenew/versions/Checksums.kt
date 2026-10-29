package gradlenew.versions

data class Checksums(val distribution: String, val wrapper: String) {
    init {
        require(isSha256(distribution)) { "Not a lower-case SHA-256 digest: '$distribution'" }
        require(isSha256(wrapper)) { "Not a lower-case SHA-256 digest: '$wrapper'" }
    }

    companion object {
        fun isSha256(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
