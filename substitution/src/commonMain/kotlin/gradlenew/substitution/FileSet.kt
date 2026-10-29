package gradlenew.substitution

class FileEntry(val path: String, val content: ByteArray, val executable: Boolean) {
    init {
        require(path.isNotEmpty() && !path.startsWith("/") && !path.contains('\\')) { "Invalid entry path '$path'" }
        require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid entry path '$path'" }
    }

    fun withPath(newPath: String) = FileEntry(newPath, content, executable)

    override fun equals(other: Any?) =
        other is FileEntry && path == other.path && executable == other.executable && content.contentEquals(other.content)

    override fun hashCode() = (path.hashCode() * 31 + content.contentHashCode()) * 31 + executable.hashCode()

    override fun toString() = "FileEntry($path, ${content.size} bytes${if (executable) ", executable" else ""})"
}

class FileSet private constructor(val entries: List<FileEntry>) {

    fun entry(path: String): FileEntry? = entries.firstOrNull { it.path == path }

    fun under(root: String): FileSet = of(entries.map { it.withPath("$root/${it.path}") })

    fun map(transform: (FileEntry) -> FileEntry): FileSet = of(entries.map(transform))

    fun differencesFrom(actual: FileSet): List<String> {
        val expectedByPath = entries.associateBy { it.path }
        val actualByPath = actual.entries.associateBy { it.path }
        return (expectedByPath.keys + actualByPath.keys).sorted().mapNotNull { path ->
            val expected = expectedByPath[path]
            val found = actualByPath[path]
            when {
                expected == null -> "$path: unexpected"
                found == null -> "$path: missing"
                !expected.content.contentEquals(found.content) -> "$path: content differs"
                expected.executable != found.executable -> "$path: executable ${found.executable}, expected ${expected.executable}"
                else -> null
            }
        }
    }

    override fun equals(other: Any?) = other is FileSet && entries == other.entries

    override fun hashCode() = entries.hashCode()

    override fun toString() = "FileSet(${entries.map { it.path }})"

    companion object {
        fun of(vararg entries: FileEntry): FileSet = of(entries.toList())

        fun of(entries: List<FileEntry>): FileSet {
            val duplicates = entries.groupingBy { it.path }.eachCount().filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) { "Duplicate entries $duplicates" }
            return FileSet(entries.sortedBy { it.path })
        }
    }
}
